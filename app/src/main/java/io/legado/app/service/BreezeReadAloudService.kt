package io.legado.app.service

import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.inkpaper.tts.BreezeAiDirector
import io.legado.app.inkpaper.tts.BreezeApiClient
import io.legado.app.inkpaper.tts.BreezeAudioCache
import io.legado.app.inkpaper.tts.BreezeAuthException
import io.legado.app.inkpaper.tts.BreezeCastStore
import io.legado.app.inkpaper.tts.BreezeCreditExhaustedException
import io.legado.app.inkpaper.tts.BreezeDramaMode
import io.legado.app.inkpaper.tts.BreezePlaybackTracker
import io.legado.app.inkpaper.tts.BreezeScriptParser
import io.legado.app.inkpaper.tts.BreezeTtsConfig
import io.legado.app.inkpaper.tts.DramaSpeechSegment
import io.legado.app.inkpaper.tts.QuoteAttribution
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val MAX_BREEZE_QUEUE_ITEMS = 6
private const val BREEZE_MEDIA_PREFIX = "breeze:"
private const val BREEZE_PAUSE_PREFIX = "breeze-pause:"
private const val INTRA_PARAGRAPH_PAUSE_MS = 120
private const val INTER_PARAGRAPH_PAUSE_MS = 320

class BreezeReadAloudService : BaseReadAloudService(), Player.Listener {

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build()
    }

    private val playbackSessionId = AtomicLong()
    private val completedParagraphUpTo = AtomicInteger(-1)
    private var producerJob: Job? = null
    private var directorJob: Job? = null
    private var playIndexJob: Job? = null
    private var playErrorCount = 0

    private val segmentMetaMap = ConcurrentHashMap<String, SegmentPlaybackMeta>()

    private data class SegmentPlaybackMeta(
        val sessionId: Long,
        val paragraphIndex: Int,
        val segmentIndex: Int,
        val isLastInParagraph: Boolean,
        val charOffsetInParagraph: Int,
        val charLength: Int,
        val speaker: String,
        val emotion: String,
        val voiceName: String,
        val directorBadge: String,
    )

    override fun onCreate() {
        super.onCreate()
        exoPlayer.addListener(this)
        applyLocalPlaybackSpeed()
    }

    override fun onDestroy() {
        invalidatePlaybackSession()
        cancelJobs()
        BreezePlaybackTracker.reset()
        super.onDestroy()
        exoPlayer.release()
    }

    override fun play() {
        if (paragraphMode && paragraphWaitingForChapter) return
        pageChanged = false
        playStop()
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("Breeze 朗读列表为空")
            ReadBook.readAloud()
            return
        }
        if (!BreezeTtsConfig.hasApiKey()) {
            BreezePlaybackTracker.update {
                it.copy(
                    isRunning = true,
                    isBuffering = false,
                    errorMessage = "请先在听书设置中填写 Breeze API Key",
                )
            }
            toastOnUi("请先在听书设置中填写 Breeze API Key")
            pauseReadAloud()
            return
        }

        super.play()
        applyLocalPlaybackSpeed()
        val sessionId = playbackSessionId.incrementAndGet()
        completedParagraphUpTo.set(nowSpeak - 1)
        playErrorCount = 0
        BreezePlaybackTracker.update {
            it.copy(
                isRunning = true,
                isBuffering = true,
                speaker = "准备中",
                emotion = BreezeTtsConfig.dramaMode.label,
                errorMessage = null,
            )
        }
        startBreezeProducer(sessionId)
    }

    override fun playStop() {
        invalidatePlaybackSession()
        cancelJobs()
        segmentMetaMap.clear()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        playIndexJob?.cancel()
    }

    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        runCatching {
            playIndexJob?.cancel()
            exoPlayer.pause()
        }
        BreezePlaybackTracker.update { it.copy(isBuffering = false) }
    }

    override fun resumeReadAloud() {
        super.resumeReadAloud()
        runCatching {
            if (pageChanged) {
                play()
            } else {
                applyLocalPlaybackSpeed()
                exoPlayer.play()
                updateCurrentPlaybackTracking()
            }
        }
    }

    override fun upSpeechRate(reset: Boolean) {
        applyLocalPlaybackSpeed()
    }

    private fun applyLocalPlaybackSpeed() {
        val speed = BreezeTtsConfig.speedMultiplier
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    private fun startBreezeProducer(sessionId: Long) {
        val bookUrl = ReadBook.book?.bookUrl.orEmpty()
        val chapterUrl = textChapter?.chapter?.url.orEmpty()
        val paragraphsSnapshot = contentList.toList()
        val firstIndex = nowSpeak.coerceAtLeast(0)
        val initialTrimOffset = if (!paragraphMode && paragraphStartPos > 0) paragraphStartPos else 0
        val dramaMode = BreezeTtsConfig.dramaMode
        val stylePreset = BreezeTtsConfig.stylePreset
        val modelId = BreezeTtsConfig.modelId

        producerJob = lifecycleScope.launch(IO) {
            try {
                if (BreezeCastStore.instance.getVoiceCatalog().isEmpty()) {
                    BreezeApiClient.syncVoices().onFailure { err ->
                        AppLog.put("Breeze 同步音色库失败: ${err.localizedMessage}", err)
                    }
                }
                ensureSessionActive(sessionId)

                val knownCharacters = BreezeCastStore.instance.getBookCast(bookUrl).map { it.name }
                val baseChapterSegments = BreezeScriptParser.parseChapter(
                    paragraphs = paragraphsSnapshot,
                    mode = dramaMode,
                    stylePreset = stylePreset,
                    knownCharacters = knownCharacters,
                )

                val liveAttributions = ConcurrentHashMap<Int, QuoteAttribution>()
                var directorActive = false
                val firstBatchReady = CompletableDeferred<Unit>()
                if (dramaMode == BreezeDramaMode.AI_DIRECTOR && BreezeTtsConfig.hasLlmConfig()) {
                    val cached = BreezeAiDirector.instance.loadCachedAttributions(
                        bookUrl = bookUrl,
                        chapterUrl = chapterUrl,
                        paragraphs = paragraphsSnapshot,
                    )
                    if (cached != null) {
                        liveAttributions.putAll(cached)
                        directorActive = true
                        firstBatchReady.complete(Unit)
                    } else {
                        directorActive = true
                        directorJob = lifecycleScope.launch(IO) {
                            try {
                                BreezeAiDirector.instance.analyzeChapter(
                                    bookUrl = bookUrl,
                                    chapterUrl = chapterUrl,
                                    paragraphs = paragraphsSnapshot,
                                    baseChapterSegments = baseChapterSegments,
                                    onBatchReady = { batchMap ->
                                        liveAttributions.putAll(batchMap)
                                        firstBatchReady.complete(Unit)
                                    },
                                )
                            } catch (err: Throwable) {
                                AppLog.put("Breeze AI 导演解析失败，已回退本地规则: ${err.localizedMessage}", err)
                            } finally {
                                firstBatchReady.complete(Unit)
                            }
                        }
                    }
                } else {
                    firstBatchReady.complete(Unit)
                }

                var preAnalyzedNextChapter = false

                for (pIndex in firstIndex until paragraphsSnapshot.size) {
                    ensureActive()
                    ensureSessionActive(sessionId)

                    val rawBase = baseChapterSegments.getOrNull(pIndex).orEmpty()
                    if (rawBase.isEmpty()) continue

                    if (directorActive && !firstBatchReady.isCompleted && rawBase.any { it.isDialogue }) {
                        withTimeoutOrNull(3_000L) { firstBatchReady.await() }
                    }

                    val attrSnapshot = if (liveAttributions.isNotEmpty()) liveAttributions.toMap() else emptyMap()
                    var segments = if (attrSnapshot.isNotEmpty()) {
                        BreezeAiDirector.applyAttributions(listOf(rawBase), attrSnapshot).first()
                    } else {
                        rawBase
                    }

                    if (pIndex == firstIndex && initialTrimOffset > 0 && !paragraphMode) {
                        val rawParagraph = paragraphsSnapshot[pIndex]
                        val sliced = rawParagraph.substring(initialTrimOffset.coerceAtMost(rawParagraph.length))
                        segments = BreezeScriptParser.parseParagraph(
                            paragraphIndex = pIndex,
                            rawText = sliced,
                            mode = dramaMode,
                            stylePreset = stylePreset,
                            knownCharacters = knownCharacters,
                        ).map { seg ->
                            seg.copy(charOffsetInParagraph = seg.charOffsetInParagraph + initialTrimOffset)
                        }
                        if (segments.isEmpty()) continue
                    }

                    for (sIndex in segments.indices) {
                        ensureActive()
                        ensureSessionActive(sessionId)

                        val seg = segments[sIndex]
                        val isLastInParagraph = sIndex == segments.lastIndex
                        val isAiAttributed = dramaMode == BreezeDramaMode.AI_DIRECTOR &&
                            seg.quoteGlobalIndex >= 0 &&
                            liveAttributions.containsKey(seg.quoteGlobalIndex)
                        val resolved = BreezeCastStore.instance.resolveVoiceForSegment(
                            bookUrl = bookUrl,
                            segment = seg,
                            defaultNarratorId = BreezeTtsConfig.narratorVoiceId,
                            defaultMaleId = BreezeTtsConfig.maleVoiceId,
                            defaultFemaleId = BreezeTtsConfig.femaleVoiceId,
                            defaultElderId = BreezeTtsConfig.elderVoiceId,
                            persistNewCharacter = dramaMode != BreezeDramaMode.AI_DIRECTOR || isAiAttributed,
                        )

                        val audioFile = fetchOrGetCachedAudio(
                            sessionId = sessionId,
                            modelId = modelId,
                            voiceId = resolved.voiceId,
                            instruction = seg.emotionInstruction,
                            text = seg.text,
                        )

                        val mediaId = "$BREEZE_MEDIA_PREFIX$sessionId:$pIndex:$sIndex:$isLastInParagraph"
                        val directorBadge = when {
                            dramaMode == BreezeDramaMode.AI_DIRECTOR && directorActive -> "AI导演"
                            dramaMode == BreezeDramaMode.LOCAL_MULTI_VOICE -> "旁白/对白"
                            else -> "说书模式"
                        }
                        segmentMetaMap[mediaId] = SegmentPlaybackMeta(
                            sessionId = sessionId,
                            paragraphIndex = pIndex,
                            segmentIndex = sIndex,
                            isLastInParagraph = isLastInParagraph,
                            charOffsetInParagraph = seg.charOffsetInParagraph,
                            charLength = seg.charLength,
                            speaker = resolved.speakerDisplay,
                            emotion = seg.emotionInstruction,
                            voiceName = resolved.voiceName,
                            directorBadge = directorBadge,
                        )

                        val itemsToEnqueue = ArrayList<MediaItem>(2)
                        itemsToEnqueue.add(
                            MediaItem.Builder()
                                .setUri(Uri.fromFile(audioFile))
                                .setMediaId(mediaId)
                                .build(),
                        )

                        val hasNextSegment = !isLastInParagraph || pIndex < paragraphsSnapshot.lastIndex
                        if (hasNextSegment) {
                            val pauseMs = if (isLastInParagraph) INTER_PARAGRAPH_PAUSE_MS else INTRA_PARAGRAPH_PAUSE_MS
                            val pauseFile = BreezeAudioCache.instance.getOrCreateSilenceFile(
                                durationMs = pauseMs,
                                wavBytes = generateSilentWavBytes(pauseMs),
                            )
                            itemsToEnqueue.add(
                                MediaItem.Builder()
                                    .setUri(Uri.fromFile(pauseFile))
                                    .setMediaId("$BREEZE_PAUSE_PREFIX$sessionId:$pauseMs")
                                    .build(),
                            )
                        }

                        enqueueItemsWithBackpressure(sessionId, itemsToEnqueue)
                    }

                    if (!preAnalyzedNextChapter &&
                        dramaMode == BreezeDramaMode.AI_DIRECTOR &&
                        BreezeTtsConfig.hasLlmConfig() &&
                        pIndex >= (paragraphsSnapshot.size * 0.6).toInt()
                    ) {
                        preAnalyzedNextChapter = true
                        preAnalyzeNextChapterScript(bookUrl, dramaMode, stylePreset, knownCharacters)
                    }
                }

                BreezeAudioCache.instance.trimToMaxBytes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BreezeCreditExhaustedException) {
                AppLog.put("Breeze 积分不足", e, true)
                handleCreditExhausted(sessionId)
            } catch (e: BreezeAuthException) {
                AppLog.put("Breeze 鉴权失败", e, true)
                withContext(Main) {
                    if (isSessionActive(sessionId)) {
                        BreezePlaybackTracker.update {
                            it.copy(isBuffering = false, errorMessage = e.localizedMessage)
                        }
                        toastOnUi(e.localizedMessage ?: "Breeze API Key 无效")
                        pauseReadAloud()
                    }
                }
            } catch (e: Exception) {
                AppLog.put("Breeze 朗读合成出错: ${e.localizedMessage}", e, true)
                withContext(Main) {
                    if (isSessionActive(sessionId)) {
                        BreezePlaybackTracker.update {
                            it.copy(isBuffering = false, errorMessage = e.localizedMessage)
                        }
                        toastOnUi("Breeze 合成失败：${e.localizedMessage}")
                        pauseReadAloud()
                    }
                }
            }
        }
    }

    private suspend fun fetchOrGetCachedAudio(
        sessionId: Long,
        modelId: String,
        voiceId: String,
        instruction: String,
        text: String,
    ): File {
        val key = BreezeAudioCache.instance.cacheKey(modelId, voiceId, instruction, text)
        BreezeAudioCache.instance.getReusableFile(key)?.let { return it }

        val queueEmpty = withContext(Main) { exoPlayer.mediaItemCount == 0 }
        if (queueEmpty) {
            BreezePlaybackTracker.update { it.copy(isBuffering = true) }
        }

        val stream = BreezeApiClient.streamSpeech(
            voiceId = voiceId,
            text = text,
            instruction = instruction,
            modelId = modelId,
        )
        return stream.use { input ->
            BreezeAudioCache.instance.writeStream(key, input) {
                ensureSessionActive(sessionId)
            }
        }
    }

    private fun preAnalyzeNextChapterScript(
        bookUrl: String,
        dramaMode: BreezeDramaMode,
        stylePreset: io.legado.app.inkpaper.tts.BreezeStylePreset,
        knownCharacters: List<String>,
    ) {
        val nextChapter = ReadBook.nextTextChapter ?: return
        val nextParagraphs = if (paragraphMode) {
            nextChapter.paragraphItems.map { it.text.toString() }
        } else {
            nextChapter.getNeedReadAloud(0, readAloudByPage, 0, 1)
                .split("\n")
                .filter { it.isNotEmpty() }
        }
        if (nextParagraphs.isEmpty()) return
        val nextSegments = BreezeScriptParser.parseChapter(
            paragraphs = nextParagraphs,
            mode = dramaMode,
            stylePreset = stylePreset,
            knownCharacters = knownCharacters,
        )
        lifecycleScope.launch(IO) {
            runCatching {
                BreezeAiDirector.instance.analyzeChapter(
                    bookUrl = bookUrl,
                    chapterUrl = nextChapter.chapter.url,
                    paragraphs = nextParagraphs,
                    baseChapterSegments = nextSegments,
                    onBatchReady = {},
                )
            }
        }
    }

    private suspend fun handleCreditExhausted(sessionId: Long) {
        withContext(Main) {
            if (!isSessionActive(sessionId)) return@withContext
            if (BreezeTtsConfig.autoFallbackOnCreditExhausted) {
                toastOnUi("Breeze 积分已用尽，已自动切回系统朗读")
                ReadBook.book?.setTtsEngine(null)
                AppConfig.ttsEngine = ""
                ReadAloud.upReadAloudClass()
                ReadBook.readAloud(play = true)
            } else {
                BreezePlaybackTracker.update {
                    it.copy(isBuffering = false, errorMessage = "Breeze 积分已用尽")
                }
                toastOnUi("Breeze 积分已用尽，请充值或更换引擎")
                pauseReadAloud()
            }
        }
    }

    private suspend fun enqueueItemsWithBackpressure(sessionId: Long, items: List<MediaItem>) {
        while (true) {
            currentCoroutineContext().ensureActive()
            ensureSessionActive(sessionId)
            if (pause) {
                delay(800L)
                continue
            }
            val hasSpace = withContext(Main) {
                if (!isSessionActive(sessionId)) return@withContext false
                trimPlayedMediaItems()
                exoPlayer.mediaItemCount + items.size <= MAX_BREEZE_QUEUE_ITEMS
            }
            if (hasSpace) break
            delay(80L)
        }
        withContext(Main) {
            if (!isSessionActive(sessionId)) return@withContext
            items.forEach(exoPlayer::addMediaItem)
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED &&
            !timeline.isEmpty &&
            exoPlayer.playbackState == Player.STATE_IDLE
        ) {
            exoPlayer.prepare()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                BreezePlaybackTracker.update { it.copy(isBuffering = true) }
            }

            Player.STATE_READY -> {
                if (pause) return
                val current = exoPlayer.currentMediaItem ?: return
                if (!isCurrentSessionMediaItem(current)) return
                applyLocalPlaybackSpeed()
                exoPlayer.play()
                BreezePlaybackTracker.update { it.copy(isBuffering = false) }
                if (!isPauseMediaItem(current)) {
                    updateCurrentPlaybackTracking()
                }
            }

            Player.STATE_ENDED -> {
                val current = exoPlayer.currentMediaItem
                if (current != null && !isCurrentSessionMediaItem(current)) return
                playErrorCount = 0
                val meta = current?.let { segmentMetaMap[it.mediaId] }
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                if (meta != null && meta.isLastInParagraph) {
                    advanceAfterParagraph(meta.paragraphIndex)
                } else {
                    advanceAfterParagraph(nowSpeak)
                }
            }

            Player.STATE_IDLE -> Unit
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
        if (!isCurrentSessionMediaItem(mediaItem)) return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            playErrorCount = 0
        }
        trimPlayedMediaItems()
        if (isPauseMediaItem(mediaItem)) return

        val meta = mediaItem?.let { segmentMetaMap[it.mediaId] }
        if (meta != null) {
            val prevParagraph = meta.paragraphIndex - 1
            if (prevParagraph > completedParagraphUpTo.get() && prevParagraph >= 0) {
                advanceAfterParagraph(prevParagraph)
            }
        }
        updateCurrentPlaybackTracking()
    }

    override fun onPlayerError(error: PlaybackException) {
        val item = exoPlayer.currentMediaItem ?: return
        if (!isCurrentSessionMediaItem(item)) return
        trimPlayedMediaItems()
        if (isPauseMediaItem(item)) {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
            }
            return
        }
        playErrorCount++
        AppLog.put("Breeze 播放器出错 ($playErrorCount): ${error.localizedMessage}", error)
        if (playErrorCount >= 4) {
            toastOnUi("Breeze 连续播放错误，已暂停")
            pauseReadAloud()
        } else if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.prepare()
        } else {
            exoPlayer.clearMediaItems()
            advanceAfterParagraph(nowSpeak)
        }
    }

    private fun updateCurrentPlaybackTracking() {
        playIndexJob?.cancel()
        val current = exoPlayer.currentMediaItem ?: return
        val meta = segmentMetaMap[current.mediaId] ?: return
        if (!isSessionActive(meta.sessionId)) return

        BreezePlaybackTracker.update {
            it.copy(
                isRunning = true,
                isBuffering = false,
                speaker = meta.speaker,
                emotion = meta.emotion,
                voiceName = meta.voiceName,
                directorBadge = meta.directorBadge,
                errorMessage = null,
            )
        }

        if (paragraphMode) {
            if (!pause) {
                paragraphStarted(meta.paragraphIndex)
            }
            return
        }

        val chapter = textChapter ?: return
        val paragraphBase = readAloudNumber - paragraphStartPos
        val segStartPos = (paragraphBase + meta.charOffsetInParagraph).coerceAtLeast(0)
        upTtsProgress(segStartPos)

        val durationMs = exoPlayer.duration
        if (durationMs <= 0 || meta.charLength <= 0) return

        playIndexJob = lifecycleScope.launch {
            val stepMs = (durationMs / meta.charLength.toFloat()).coerceAtLeast(25f).toLong()
            val startOffset = (meta.charLength * exoPlayer.currentPosition / durationMs)
                .toInt()
                .coerceIn(0, meta.charLength)
            for (i in startOffset..meta.charLength) {
                val globalPos = segStartPos + i
                if (pageIndex + 1 < chapter.pageSize && globalPos > chapter.getReadLength(pageIndex + 1)) {
                    pageIndex++
                    ReadBook.moveToNextPage(syncReadAloudFollow = true)
                    upTtsProgress(globalPos)
                }
                delay(stepMs)
            }
        }
    }

    private fun advanceAfterParagraph(finishedParagraphIndex: Int) {
        if (finishedParagraphIndex <= completedParagraphUpTo.get()) return
        completedParagraphUpTo.set(finishedParagraphIndex)

        if (paragraphMode) {
            paragraphFinished(finishedParagraphIndex)
            return
        }

        val currentLen = contentList.getOrNull(finishedParagraphIndex)?.length ?: 0
        readAloudNumber += currentLen + 1 - paragraphStartPos
        paragraphStartPos = 0
        if (finishedParagraphIndex < contentList.lastIndex) {
            nowSpeak = finishedParagraphIndex + 1
        } else {
            nextChapter(auto = true)
        }
    }

    private fun trimPlayedMediaItems() {
        val currentIndex = exoPlayer.currentMediaItemIndex
        if (currentIndex > 0) {
            for (i in 0 until currentIndex) {
                segmentMetaMap.remove(exoPlayer.getMediaItemAt(i).mediaId)
            }
            exoPlayer.removeMediaItems(0, currentIndex)
        }
    }

    private fun isPauseMediaItem(mediaItem: MediaItem?): Boolean {
        return mediaItem?.mediaId?.startsWith(BREEZE_PAUSE_PREFIX) == true
    }

    private fun isCurrentSessionMediaItem(mediaItem: MediaItem?): Boolean {
        val id = mediaItem?.mediaId ?: return false
        val sessionPart = when {
            id.startsWith(BREEZE_MEDIA_PREFIX) -> id.removePrefix(BREEZE_MEDIA_PREFIX)
            id.startsWith(BREEZE_PAUSE_PREFIX) -> id.removePrefix(BREEZE_PAUSE_PREFIX)
            else -> return false
        }
        val sessionId = sessionPart.substringBefore(':').toLongOrNull() ?: return false
        return isSessionActive(sessionId)
    }

    private fun invalidatePlaybackSession() {
        playbackSessionId.incrementAndGet()
    }

    private fun cancelJobs() {
        producerJob?.cancel()
        producerJob = null
        directorJob?.cancel()
        directorJob = null
    }

    private fun isSessionActive(sessionId: Long): Boolean = playbackSessionId.get() == sessionId

    private fun ensureSessionActive(sessionId: Long) {
        if (!isSessionActive(sessionId)) {
            throw CancellationException("Breeze playback session changed")
        }
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<BreezeReadAloudService>(actionStr)
    }
}
