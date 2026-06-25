package ai.milf.client.session

import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.protocol.MilfMessage
import ai.milf.client.ws.MilfWebSocketClient
import ai.milf.client.AudioRecorderLike
import ai.milf.client.NarratorLike
import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class MilfSessionControllerTest {
    @Test
    fun beginListeningMovesToListeningState() = runTest {
        val narrator = FakeNarrator()
        val controller = fakeController(narrator = narrator)

        controller.beginListening()

        assertEquals(SeniorUxScreen.Listening, controller.uiState.value.screen)
        assertEquals(true, controller.uiState.value.isRecording)
        assertEquals(LISTENING_PROMPT, controller.uiState.value.captions)
        assertEquals(emptyList<String>(), narrator.spoken.map { it.first })
    }

    @Test
    fun confirmationRequestShowsPlainGate() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onConfirmRequest(
            id = "c1",
            summary = "Calling Wei, your grandson?",
            lang = "en",
            contactId = "wei-grandson"
        )

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Confirming, state.screen)
        assertEquals("Calling Wei, your grandson?", state.confirmation?.summary)
    }

    @Test
    fun approveConfirmationSendsResponseAndReturnsToActing() = runTest {
        val sent = mutableListOf<ConfirmResponse>()
        val client = FakeClient(onSendConfirm = { sent += it })
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        controller.showConfirmationForTest("c1", "Calling Wei?", "en")
        controller.approveConfirmation()

        assertEquals(listOf(ConfirmResponse("c1", true)), sent)
        assertEquals(SeniorUxScreen.Acting, controller.uiState.value.screen)
        assertEquals(null, controller.uiState.value.confirmation)
    }

    @Test
    fun approveConfirmationShrinksOverlayBeforeSendingResponse() = runTest {
        var stateWhenResponseSent: SeniorUiState? = null
        lateinit var controller: MilfSessionController
        val client = FakeClient(
            onSendConfirm = {
                stateWhenResponseSent = controller.uiState.value
            }
        )
        controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        controller.showConfirmationForTest("c1", "Send WhatsApp message?", "en")
        controller.approveConfirmation()

        val state = stateWhenResponseSent
        assertEquals(SeniorUxScreen.Acting, state?.screen)
        assertEquals(null, state?.confirmation)
        assertEquals(true, state?.isCollapsed)
    }

    @Test
    fun denyConfirmationInvalidatesAndClosesActiveSession() = runTest {
        val sent = mutableListOf<ConfirmResponse>()
        val client = FakeClient(onSendConfirm = { sent += it })
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        controller.showConfirmationForTest("c1", "Calling Wei?", "en")
        controller.denyConfirmation()
        client.callbacks?.onTaskComplete("Old success.", "en", "wei-grandson")

        val state = controller.uiState.value
        assertEquals(listOf(ConfirmResponse("c1", false)), sent)
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Idle, state.screen)
        assertEquals(null, state.success)
        assertEquals(null, state.failure)
    }

    @Test
    fun taskFailureShowsMessageOnly() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onTaskFailure(
            message = "I'm having a little trouble with that. Please try again.",
            lang = "en"
        )

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(SAFE_FAILURE_COPY, state.failure?.message)
    }

    @Test
    fun taskCompleteReturnsToIdleAndIgnoresLaterFailure() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onTaskComplete("Connected to Wei.", "en", "wei-grandson")
        client.callbacks?.onTaskFailure("Old failure.", "en")

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Idle, state.screen)
        assertEquals(READY_PROMPT, state.captions)
        assertEquals(null, state.success)
        assertEquals(null, state.failure)
    }

    @Test
    fun taskCompleteSpeaksSummary() = runTest {
        val client = FakeClient()
        val narrator = FakeNarrator()
        val controller = fakeController(client = client, narrator = narrator)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onTaskComplete("Sent hello to Quick notes.", "en", null)

        assertEquals(listOf("Sent hello to Quick notes."), narrator.spoken.map { it.first })
    }

    @Test
    fun taskCompleteDoesNotRepeatExistingNarration() = runTest {
        val client = FakeClient()
        val narrator = FakeNarrator()
        val controller = fakeController(client = client, narrator = narrator)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onNarration("Which app should I use?", "en")
        client.callbacks?.onTaskComplete("Which app should I use?", "en", null)

        assertEquals(listOf("Which app should I use?"), narrator.spoken.map { it.first })
    }

    @Test
    fun taskCompleteQuestionReturnsToIdleAndKeepsQuestionVisible() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onTaskComplete("Which listed Wei is your grandson?", "en", null)

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Idle, state.screen)
        assertEquals("Which listed Wei is your grandson?", state.captions)
        assertEquals(null, state.success)
        assertEquals(null, state.failure)
    }

    @Test
    fun closedSessionLocksFailureAgainstLaterTaskComplete() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onClosed("socket closed")
        client.callbacks?.onTaskComplete("Old success.", "en", "wei-grandson")

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(SAFE_FAILURE_COPY, state.failure?.message)
        assertEquals(null, state.success)
    }

    @Test
    fun failedSessionLocksFailureAgainstLaterTaskComplete() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onFailed("websocket failed")
        client.callbacks?.onTaskComplete("Old success.", "en", "wei-grandson")

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(SAFE_FAILURE_COPY, state.failure?.message)
        assertEquals(null, state.success)
    }

    @Test
    fun confirmRequestDoesNotOverwriteSuccessIfSessionBecomesStaleDuringSpeech() = runTest {
        val client = FakeClient()
        val narrator = FakeNarrator()
        val controller = fakeController(client = client, narrator = narrator)

        controller.beginListening()
        controller.finishListeningAndRun()
        narrator.onSpeak = { text, _ ->
            if (text == "Calling Wei?") {
                runBlocking {
                    client.callbacks?.onTaskComplete("Connected to Wei.", "en", "wei-grandson")
                }
            }
        }
        client.callbacks?.onConfirmRequest("c1", "Calling Wei?", "en", "wei-grandson")

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Idle, state.screen)
        assertEquals(READY_PROMPT, state.captions)
        assertEquals(null, state.confirmation)
    }

    @Test
    fun narrationDoesNotOverwriteFailureIfSessionBecomesStaleDuringSpeech() = runTest {
        val client = FakeClient()
        val narrator = FakeNarrator()
        val controller = fakeController(client = client, narrator = narrator)

        controller.beginListening()
        controller.finishListeningAndRun()
        narrator.onSpeak = { text, _ ->
            if (text == "Still working.") {
                runBlocking {
                    client.callbacks?.onTaskFailure("Need help.", "en")
                }
            }
        }
        client.callbacks?.onNarration("Still working.", "en")

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals("Need help.", state.captions)
    }

    @Test
    fun oldTerminalCallbackDoesNotCloseNewActiveClient() = runTest {
        val clientA = FakeClient()
        val clientB = FakeClient()
        val narrator = FakeNarrator()
        val controller = fakeController(clients = listOf(clientA, clientB), narrator = narrator)

        controller.beginListening()
        controller.finishListeningAndRun()
        narrator.onSpeak = { text, _ ->
            if (text == "Connected to Wei.") {
                controller.beginListening()
                controller.finishListeningAndRun()
            }
        }
        clientA.callbacks?.onTaskComplete("Connected to Wei.", "en", "wei-grandson")

        assertEquals(true, clientA.closed)
        assertEquals(false, clientB.closed)
    }

    @Test
    fun staleCallbacksFromPreviousRunDoNotOverwriteCurrentRun() = runTest {
        val clientA = FakeClient()
        val clientB = FakeClient()
        val controller = fakeController(clients = listOf(clientA, clientB))

        controller.beginListening()
        controller.finishListeningAndRun()
        controller.beginListening()
        controller.finishListeningAndRun()
        clientA.callbacks?.onTaskComplete("Old success.", "en", "wei-grandson")
        clientA.callbacks?.onTaskFailure("Old failure.", "en")

        val state = controller.uiState.value
        assertEquals(true, clientA.closed)
        assertEquals(SeniorUxScreen.Thinking, state.screen)
        assertEquals(THINKING_PROMPT, state.captions)
        assertEquals(null, state.success)
        assertEquals(null, state.failure)
    }

    @Test
    fun finishWhenNotRecordingDoesNotStopRecorder() = runTest {
        val recorder = FakeRecorder()
        val controller = fakeController(recorder = recorder)

        controller.finishListeningAndRun()

        assertEquals(0, recorder.stopCalls)
        assertEquals(SeniorUxScreen.Idle, controller.uiState.value.screen)
    }

    @Test
    fun recorderStartFailureMovesToSafeFailure() = runTest {
        val recorder = FakeRecorder(failStart = true)
        val controller = fakeController(recorder = recorder)

        controller.beginListening()

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(false, state.isRecording)
        assertEquals(false, state.isRunning)
        assertEquals(SAFE_FAILURE_COPY, state.failure?.message)
    }

    @Test
    fun recorderStopFailureMovesToSafeFailure() = runTest {
        val recorder = FakeRecorder(failStop = true)
        val controller = fakeController(recorder = recorder)

        controller.beginListening()
        controller.finishListeningAndRun()

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(false, state.isRecording)
        assertEquals(false, state.isRunning)
        assertEquals(SAFE_FAILURE_COPY, state.failure?.message)
    }

    @Test
    fun checkedRecorderFailuresMoveToSafeFailure() = runTest {
        val recorder = FakeRecorder(failStartWithIo = true)
        val controller = fakeController(recorder = recorder)

        controller.beginListening()

        assertEquals(SeniorUxScreen.Failure, controller.uiState.value.screen)

        val stopRecorder = FakeRecorder(failStopWithIo = true)
        val stopController = fakeController(recorder = stopRecorder)
        stopController.beginListening()
        stopController.finishListeningAndRun()

        assertEquals(SeniorUxScreen.Failure, stopController.uiState.value.screen)
    }

    @Test
    fun clientStartFailureMovesToSafeFailureAndClosesClient() = runTest {
        val client = FakeClient(failStart = true)
        val controller = fakeController(client)

        controller.beginListening()
        controller.finishListeningAndRun()

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(false, state.isRecording)
        assertEquals(false, state.isRunning)
        assertEquals(SAFE_FAILURE_COPY, state.failure?.message)
    }

    @Test
    fun activeCloseWhileWorkingMovesToFailure() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onClosed("socket closed")

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(false, state.isRunning)
        assertEquals(SAFE_FAILURE_COPY, state.failure?.message)
    }

    @Test
    fun cancelActiveSessionStopsWorkAndReturnsToIdle() = runTest {
        val client = FakeClient()
        val recorder = FakeRecorder()
        val narrator = FakeNarrator()
        val controller = fakeController(
            clients = listOf(client),
            recorder = recorder,
            narrator = narrator
        )

        controller.beginListening()
        controller.finishListeningAndRun()
        controller.showConfirmationForTest("c1", "Calling Wei?", "en")
        val stopCallsBeforeCancel = narrator.stopCalls
        controller.cancelActiveSession()
        client.callbacks?.onTaskComplete("Old success.", "en", "wei-grandson")

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(true, recorder.cancelled)
        assertEquals(stopCallsBeforeCancel + 1, narrator.stopCalls)
        assertEquals(SeniorUxScreen.Idle, state.screen)
        assertEquals(false, state.isRecording)
        assertEquals(false, state.isRunning)
        assertEquals(null, state.confirmation)
        assertEquals(null, state.success)
        assertEquals(null, state.failure)
    }

    @Test
    fun collapseAndExpandOverlayUpdatesRailState() = runTest {
        val client = FakeClient()
        val controller = fakeController(client)

        controller.collapseOverlay()
        assertEquals(true, controller.uiState.value.isCollapsed)

        controller.expandOverlay()
        assertEquals(false, controller.uiState.value.isCollapsed)
    }

    @Test
    fun finishListeningMovesToThinkingUntilFirstAction() = runTest {
        val client = FakeClient()
        val controller = fakeController(client)

        controller.beginListening()
        controller.finishListeningAndRun()

        assertEquals(SeniorUxScreen.Thinking, controller.uiState.value.screen)
        assertEquals(THINKING_PROMPT, controller.uiState.value.captions)
    }

    @Test
    fun nativeSpeechResultStartsTextSessionInsteadOfUploadingAudio() = runTest {
        val client = FakeClient()
        val recognizer = FakeNativeSpeechRecognizer()
        val recorder = FakeRecorder()
        val controller = fakeController(
            clients = listOf(client),
            recorder = recorder,
            speechRecognizer = recognizer,
            speechInputMode = SpeechInputMode.Native
        )
        controller.setLang("zh")

        controller.beginListening()
        controller.finishListeningAndRun()
        recognizer.deliverText("  打电话给 Wei  ")

        assertEquals("zh", recognizer.startedLang)
        assertEquals(1, recognizer.stopCalls)
        assertEquals(false, recorder.isRecording)
        assertEquals(false, client.startedAudio)
        assertEquals("打电话给 Wei", client.startedText)
        assertEquals(SeniorUxScreen.Thinking, controller.uiState.value.screen)
    }

    @Test
    fun nativeSpeechErrorReturnsToIdleWithoutFallback() = runTest {
        val client = FakeClient()
        val narrator = FakeNarrator()
        val recognizer = FakeNativeSpeechRecognizer()
        val controller = fakeController(
            clients = listOf(client),
            narrator = narrator,
            speechRecognizer = recognizer,
            speechInputMode = SpeechInputMode.Native
        )

        controller.beginListening()
        recognizer.deliverError("no speech")

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Idle, state.screen)
        assertEquals(false, state.isRecording)
        assertEquals(false, state.isRunning)
        assertEquals("I didn't hear anything. Please try again.", state.captions)
        assertEquals(null, state.failure)
        assertEquals(null, client.startedText)
        assertEquals(false, client.startedAudio)
        assertEquals(emptyList<String>(), narrator.spoken.map { it.first })
    }

    @Test
    fun speechInputModeConfigControlsMicPath() = runTest {
        val client = FakeClient()
        val recognizer = FakeNativeSpeechRecognizer()
        val recorder = FakeRecorder()
        val controller = fakeController(
            clients = listOf(client),
            recorder = recorder,
            speechRecognizer = recognizer,
            speechInputMode = SpeechInputMode.Native
        )

        assertEquals(SpeechInputMode.Native, controller.uiState.value.speechInputMode)
        controller.setSpeechInputMode(SpeechInputMode.BackendAudio)
        controller.setAgentMemoryDraft("Wei is my grandson.")
        controller.saveAgentMemory()
        controller.beginListening()
        controller.finishListeningAndRun()

        assertEquals(SpeechInputMode.BackendAudio, controller.uiState.value.speechInputMode)
        assertEquals(null, recognizer.startedLang)
        assertEquals(1, recorder.stopCalls)
        assertEquals(true, client.startedAudio)
        assertEquals("Wei is my grandson.", client.startedMemory)
        assertEquals(null, client.startedText)
    }

    @Test
    fun setSpeechInputModePersistsChoice() = runTest {
        val saved = mutableListOf<SpeechInputMode>()
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                saveSpeechInputMode = { saved += it }
            )
        )

        controller.setSpeechInputMode(SpeechInputMode.BackendAudio)

        assertEquals(listOf(SpeechInputMode.BackendAudio), saved)
        assertEquals(SpeechInputMode.BackendAudio, controller.uiState.value.speechInputMode)
    }

    @Test
    fun setAgentMemoryDraftMarksMemoryUnsaved() = runTest {
        val saved = mutableListOf<String>()
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                initialAgentMemory = "Existing memory",
                saveAgentMemory = { saved += it }
            )
        )

        assertEquals("Existing memory", controller.uiState.value.agentMemory)

        controller.setAgentMemoryDraft("Wei is my grandson.")

        assertEquals(emptyList<String>(), saved)
        assertEquals("Wei is my grandson.", controller.uiState.value.agentMemory)
        assertEquals(AgentMemorySaveStatus.Unsaved, controller.uiState.value.agentMemorySaveStatus)
    }

    @Test
    fun saveAgentMemoryPersistsDraftAndMarksSaved() = runTest {
        val saved = mutableListOf<String>()
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                saveAgentMemory = { saved += it }
            )
        )

        controller.setAgentMemoryDraft("Wei is my grandson.")
        controller.saveAgentMemory()

        assertEquals(listOf("Wei is my grandson."), saved)
        assertEquals(AgentMemorySaveStatus.Saved, controller.uiState.value.agentMemorySaveStatus)
    }

    @Test
    fun saveAgentMemoryShowsFailureWhenPersistenceFails() = runTest {
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                saveAgentMemory = { error("disk full") }
            )
        )

        controller.setAgentMemoryDraft("Wei is my grandson.")
        controller.saveAgentMemory()

        assertEquals("Wei is my grandson.", controller.uiState.value.agentMemory)
        assertEquals(AgentMemorySaveStatus.Failed, controller.uiState.value.agentMemorySaveStatus)
    }

    @Test
    fun unsavedAgentMemoryDraftIsNotSentToBackend() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.setAgentMemoryDraft("Unsaved memory")
        controller.setCommandText("call Wei")
        controller.submitTextCommand()

        assertEquals("", client.startedMemory)
        assertEquals(AgentMemorySaveStatus.Unsaved, controller.uiState.value.agentMemorySaveStatus)
    }

    @Test
    fun savedAgentMemoryIsSentToBackend() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.setAgentMemoryDraft("Wei is my grandson.")
        controller.saveAgentMemory()
        controller.setCommandText("call Wei")
        controller.submitTextCommand()

        assertEquals("Wei is my grandson.", client.startedMemory)
    }

    @Test
    fun narrationUpdatesDedicatedReplyWithoutReplacingStatusCaption() = runTest {
        val client = FakeClient()
        val controller = fakeController(client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onNarration("I found Wei. Opening WhatsApp now.", "en")

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Thinking, state.screen)
        assertEquals(THINKING_PROMPT, state.captions)
        assertEquals("I found Wei. Opening WhatsApp now.", state.lastNarration)
    }

    @Test
    fun firstActionMovesToActingAndSetsActionTarget() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onAction(
            Action(
                id = "a1",
                name = "tap",
                args = mapOf("x" to 100, "y" to 200)
            )
        )

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Acting, state.screen)
        assertEquals(ACTING_PROMPT, state.captions)
        assertEquals(ActionTarget(x = 52, y = 168, width = 96, height = 64), state.actionTarget)
    }

    @Test
    fun submitTextCommandStartsTextSessionAndMovesToThinking() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.setAgentMemoryDraft("Wei is my grandson.")
        controller.saveAgentMemory()
        controller.setCommandText("  I want to see my grandson  ")
        controller.submitTextCommand()

        assertEquals("I want to see my grandson", client.startedText)
        assertEquals("Wei is my grandson.", client.startedMemory)
        assertEquals(SeniorUxScreen.Thinking, controller.uiState.value.screen)
        assertEquals("", controller.uiState.value.commandText)
    }

    @Test
    fun textCommandsReuseBackendSessionIdAcrossNewSocketClients() = runTest {
        val firstClient = FakeClient()
        val secondClient = FakeClient()
        val controller = fakeController(clients = listOf(firstClient, secondClient))

        controller.setCommandText("search movie")
        controller.submitTextCommand()
        firstClient.callbacks?.onTaskComplete("Which app should I use?", "en", null)

        controller.setCommandText("YT")
        controller.submitTextCommand()

        assertEquals("search movie", firstClient.startedText)
        assertEquals("YT", secondClient.startedText)
        assertEquals(firstClient.backendSessionId, secondClient.backendSessionId)
        assertEquals(false, firstClient.backendSessionId.isNullOrBlank())
    }

    @Test
    fun runStopClosesClientAndReturnsToIdleWithoutRemovingOverlay() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        controller.stopActiveRun()

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Idle, state.screen)
        assertEquals(false, state.isRecording)
        assertEquals(false, state.isRunning)
        assertEquals(READY_PROMPT, state.captions)
    }

    @Test
    fun actionTargetClearsWhenRunEnds() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onAction(
            Action(
                id = "a1",
                name = "tap",
                args = mapOf("x" to 100, "y" to 200)
            )
        )
        client.callbacks?.onTaskFailure("Need help.", "en")

        assertEquals(null, controller.uiState.value.actionTarget)
    }

    @Test
    fun startsWithPersistedBackendUrl() = runTest {
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                initialBackendUrl = "ws://192.168.1.20:8765"
            )
        )

        assertEquals("ws://192.168.1.20:8765", controller.uiState.value.backendUrl)
    }

    @Test
    fun setBackendUrlPersistsTrimmedValue() = runTest {
        val saved = mutableListOf<String>()
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                saveBackendUrl = { saved += it }
            )
        )

        controller.setBackendUrl("  ws://192.168.1.20:8765  ")

        assertEquals("ws://192.168.1.20:8765", controller.uiState.value.backendUrl)
        assertEquals(listOf("ws://192.168.1.20:8765"), saved)
    }

    @Test
    fun refreshSetupStatusUpdatesPermissionFlags() = runTest {
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                setupStatus = {
                    SetupStatus(
                        microphoneGranted = true,
                        callPhoneGranted = false,
                        overlayGranted = true,
                        accessibilityEnabled = false,
                        assistantSelected = true
                    )
                }
            )
        )

        controller.refreshSetupStatus()

        val state = controller.uiState.value
        assertEquals(true, state.microphonePermissionGranted)
        assertEquals(false, state.callPhonePermissionGranted)
        assertEquals(true, state.overlayPermissionGranted)
        assertEquals(false, state.accessibilityEnabled)
        assertEquals(false, state.canStartHelper)
    }

    @Test
    fun startGateRequiresPermissionsAndBackend() = runTest {
        val blockedController = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                setupStatus = {
                    SetupStatus(
                        microphoneGranted = true,
                        callPhoneGranted = true,
                        overlayGranted = false,
                        accessibilityEnabled = true,
                        assistantSelected = true
                    )
                }
            )
        )

        assertEquals(false, blockedController.refreshSetupStatus().canStartHelper)

        val readyController = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                setupStatus = {
                    SetupStatus(
                        microphoneGranted = true,
                        callPhoneGranted = true,
                        overlayGranted = true,
                        accessibilityEnabled = true,
                        assistantSelected = true
                    )
                }
            )
        )

        readyController.setBackendConnectionStatus(BackendConnectionStatus.Connected)
        assertEquals(true, readyController.refreshSetupStatus().canStartHelper)
        readyController.setBackendUrl(" ")
        assertEquals(false, readyController.uiState.value.canStartHelper)
    }

    @Test
    fun startGateRequiresAssistantAndLanguage() = runTest {
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                setupStatus = {
                    SetupStatus(
                        microphoneGranted = true,
                        callPhoneGranted = true,
                        overlayGranted = true,
                        accessibilityEnabled = true,
                        assistantSelected = false
                    )
                }
            )
        )

        controller.setBackendConnectionStatus(BackendConnectionStatus.Connected)
        assertEquals(false, controller.refreshSetupStatus().canStartHelper)

        val readyController = fakeController(client = FakeClient())
        readyController.setBackendConnectionStatus(BackendConnectionStatus.Connected)
        readyController.refreshSetupStatus()
        readyController.setLang("")
        assertEquals(false, readyController.uiState.value.canStartHelper)
    }

    @Test
    fun backendConnectionMustBeCheckedBeforeStartGateOpens() = runTest {
        val controller = MilfSessionController(
            dependencies = testDependencies(clients = listOf(FakeClient()))
        )

        controller.refreshSetupStatus()

        assertEquals(BackendConnectionStatus.Unknown, controller.uiState.value.backendConnectionStatus)
        assertEquals(false, controller.uiState.value.canStartHelper)

        controller.setBackendConnectionStatus(BackendConnectionStatus.Connected)

        assertEquals(true, controller.uiState.value.canStartHelper)

        controller.setBackendConnectionStatus(BackendConnectionStatus.Failed)

        assertEquals(false, controller.uiState.value.canStartHelper)
    }

    @Test
    fun assistEntryCanStartBeforeBackendConnectionIsPrechecked() = runTest {
        val controller = MilfSessionController(
            dependencies = testDependencies(clients = listOf(FakeClient()))
        )

        controller.refreshSetupStatus()

        assertEquals(BackendConnectionStatus.Unknown, controller.uiState.value.backendConnectionStatus)
        assertEquals(false, controller.uiState.value.canStartHelper)
        assertEquals(true, controller.uiState.value.canStartAssistEntry)
    }

    @Test
    fun editingBackendUrlResetsBackendConnectionStatus() = runTest {
        val controller = MilfSessionController(
            dependencies = testDependencies(clients = listOf(FakeClient()))
        )
        controller.setBackendConnectionStatus(BackendConnectionStatus.Connected)

        controller.setBackendUrl("ws://192.168.1.30:8765")

        assertEquals(BackendConnectionStatus.Unknown, controller.uiState.value.backendConnectionStatus)
    }

    @Test
    fun disconnectBackendClearsConnectionAndIgnoresStaleCheckResult() = runTest {
        var callback: ((BackendConnectionStatus) -> Unit)? = null
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                checkBackendConnection = { _, result -> callback = result }
            )
        )

        controller.refreshBackendConnection()
        controller.disconnectBackend()
        callback?.invoke(BackendConnectionStatus.Connected)

        assertEquals(false, controller.uiState.value.backendConnectionRequested)
        assertEquals(BackendConnectionStatus.Disconnected, controller.uiState.value.backendConnectionStatus)
        assertEquals(false, controller.uiState.value.canStartHelper)
    }

    @Test
    fun autoBackendRefreshCanTurnConnectedIntoFailed() = runTest {
        var callback: ((BackendConnectionStatus) -> Unit)? = null
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                checkBackendConnection = { _, result -> callback = result }
            )
        )
        controller.setBackendConnectionStatus(BackendConnectionStatus.Connected)

        controller.refreshBackendConnection()
        callback?.invoke(BackendConnectionStatus.Failed)

        assertEquals(true, controller.uiState.value.backendConnectionRequested)
        assertEquals(BackendConnectionStatus.Failed, controller.uiState.value.backendConnectionStatus)
        assertEquals(false, controller.uiState.value.canStartHelper)
    }

    @Test
    fun connectBackendEnablesAutoChecksAndStartsChecking() = runTest {
        var checks = 0
        val controller = MilfSessionController(
            dependencies = testDependencies(
                clients = listOf(FakeClient()),
                checkBackendConnection = { _, _ -> checks += 1 }
            )
        )
        controller.disconnectBackend()

        controller.connectBackend()

        assertEquals(true, controller.uiState.value.backendConnectionRequested)
        assertEquals(BackendConnectionStatus.Checking, controller.uiState.value.backendConnectionStatus)
        assertEquals(1, checks)
    }

    private fun fakeController(
        client: FakeClient = FakeClient(),
        narrator: FakeNarrator = FakeNarrator()
    ): MilfSessionController =
        fakeController(clients = listOf(client), narrator = narrator)

    private fun fakeController(
        clients: List<FakeClient>,
        recorder: FakeRecorder = FakeRecorder(),
        narrator: FakeNarrator = FakeNarrator(),
        speechRecognizer: FakeNativeSpeechRecognizer = FakeNativeSpeechRecognizer(),
        speechInputMode: SpeechInputMode = SpeechInputMode.BackendAudio
    ): MilfSessionController =
        MilfSessionController(
            dependencies = testDependencies(
                clients,
                recorder,
                narrator,
                speechRecognizer = speechRecognizer,
                speechInputMode = speechInputMode
            )
        )

    private fun fakeController(recorder: FakeRecorder): MilfSessionController =
        fakeController(clients = listOf(FakeClient()), recorder = recorder)
}

private const val SAFE_FAILURE_COPY = "I'm having a little trouble with that. Please try again."

private class FakeNarrator : NarratorLike {
    var onSpeak: (String, String) -> Unit = { _, _ -> }
    val spoken = mutableListOf<Pair<String, String>>()
    var stopCalls = 0

    override fun speak(text: String, lang: String) {
        spoken += text to lang
        onSpeak(text, lang)
    }

    override fun stop() {
        stopCalls += 1
    }

    override fun shutdown() = Unit
}

private class FakeClient(
    private val onSendConfirm: (ConfirmResponse) -> Unit = {},
    private val failStart: Boolean = false
) : SessionSocketClient {
    var callbacks: MilfWebSocketClient.Callbacks? = null
    var startedText: String? = null
    var startedAudio = false
    var backendSessionId: String? = null
    var startedMemory: String? = null

    override fun start(
        goalAudio: ByteArray,
        lang: String,
        callbacks: MilfWebSocketClient.Callbacks,
        backendSessionId: String?,
        memory: String
    ) {
        check(!failStart) { "client start failed" }
        startedAudio = true
        this.backendSessionId = backendSessionId
        startedMemory = memory
        this.callbacks = callbacks
    }

    override fun startText(
        goalText: String,
        lang: String,
        callbacks: MilfWebSocketClient.Callbacks,
        backendSessionId: String?,
        memory: String
    ) {
        check(!failStart) { "client start failed" }
        startedText = goalText
        this.backendSessionId = backendSessionId
        startedMemory = memory
        this.callbacks = callbacks
    }

    override fun send(message: MilfMessage): Boolean {
        if (message is ConfirmResponse) {
            onSendConfirm(message)
        }
        return true
    }

    var closed = false

    override fun close() {
        closed = true
    }
}

private class FakeRecorder(
    private val failStart: Boolean = false,
    private val failStop: Boolean = false,
    private val failStartWithIo: Boolean = false,
    private val failStopWithIo: Boolean = false
) : AudioRecorderLike {
    var isRecording = false
    var stopCalls = 0
    var cancelled = false

    override fun start() {
        if (failStartWithIo) throw IOException("start io failed")
        check(!failStart) { "start failed" }
        isRecording = true
        cancelled = false
    }

    override fun stop(): ByteArray {
        check(isRecording) { "stop called while not recording" }
        if (failStopWithIo) throw IOException("stop io failed")
        check(!failStop) { "stop failed" }
        stopCalls += 1
        isRecording = false
        return byteArrayOf(1)
    }

    override fun cancel() {
        cancelled = true
        isRecording = false
    }
}

private class FakeNativeSpeechRecognizer : NativeSpeechRecognizerLike {
    private var onText: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    var startedLang: String? = null
    var stopCalls = 0
    var cancelCalls = 0
    var shutdownCalls = 0

    override fun start(lang: String, onText: (String) -> Unit, onError: (String) -> Unit) {
        startedLang = lang
        this.onText = onText
        this.onError = onError
    }

    override fun stop() {
        stopCalls += 1
    }

    override fun cancel() {
        cancelCalls += 1
    }

    override fun shutdown() {
        shutdownCalls += 1
    }

    fun deliverText(text: String) {
        onText?.invoke(text)
    }

    fun deliverError(message: String) {
        onError?.invoke(message)
    }
}

private fun testDependencies(
    clients: List<FakeClient>,
    recorder: FakeRecorder = FakeRecorder(),
    narrator: FakeNarrator = FakeNarrator(),
    initialBackendUrl: String = "ws://10.0.2.2:8765",
    initialAgentMemory: String = "",
    saveBackendUrl: (String) -> Unit = {},
    saveSpeechInputMode: (SpeechInputMode) -> Unit = {},
    saveAgentMemory: (String) -> Unit = {},
    checkBackendConnection: (String, (BackendConnectionStatus) -> Unit) -> Unit = { _, callback ->
        callback(BackendConnectionStatus.Connected)
    },
    speechRecognizer: FakeNativeSpeechRecognizer = FakeNativeSpeechRecognizer(),
    speechInputMode: SpeechInputMode = SpeechInputMode.BackendAudio,
    setupStatus: () -> SetupStatus = {
        SetupStatus(
            microphoneGranted = true,
            callPhoneGranted = true,
            overlayGranted = true,
            accessibilityEnabled = true,
            assistantSelected = true
        )
    }
): MilfSessionController.Dependencies {
    var nextClient = 0
    return MilfSessionController.Dependencies(
        recorder = recorder,
        speechRecognizer = speechRecognizer,
        speechInputMode = speechInputMode,
        narrator = narrator,
        clientFactory = {
            clients.getOrElse(nextClient++) { clients.last() }
        },
        initialBackendUrl = initialBackendUrl,
        initialAgentMemory = initialAgentMemory,
        saveBackendUrl = saveBackendUrl,
        saveSpeechInputMode = saveSpeechInputMode,
        saveAgentMemory = saveAgentMemory,
        checkBackendConnection = checkBackendConnection,
        setupStatus = setupStatus,
        dispatch = { action -> ActionResult(action.id, true) }
    )
}
