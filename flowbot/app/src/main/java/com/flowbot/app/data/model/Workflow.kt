package com.flowbot.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Workflow(
    val name: String,
    val description: String = "",
    val variables: Map<String, String> = emptyMap(),
    val steps: List<WorkflowStep>
)

@Serializable
data class WorkflowStep(
    val id: String,
    val action: StepAction,
    val params: StepParams = StepParams(),
    val selector: ElementSelector? = null,
    @SerialName("on_error") val onError: ErrorPolicy = ErrorPolicy.STOP,
    @SerialName("delay_after_ms") val delayAfterMs: Long = 500,
    val output: String? = null,
    val steps: List<WorkflowStep>? = null
)

@Serializable
enum class StepAction {
    @SerialName("tap") TAP,
    @SerialName("long_press") LONG_PRESS,
    @SerialName("swipe") SWIPE,
    @SerialName("pinch") PINCH,
    @SerialName("drag") DRAG,
    @SerialName("type_text") TYPE_TEXT,
    @SerialName("press_key") PRESS_KEY,
    @SerialName("copy_clipboard") COPY_CLIPBOARD,
    @SerialName("read_clipboard") READ_CLIPBOARD,
    @SerialName("open_app") OPEN_APP,
    @SerialName("screenshot") SCREENSHOT,
    @SerialName("delay") DELAY,
    @SerialName("wait_for_element") WAIT_FOR_ELEMENT,
    @SerialName("loop") LOOP,
    @SerialName("press_home") PRESS_HOME,
    @SerialName("press_back") PRESS_BACK,
    @SerialName("press_recent") PRESS_RECENT,
    @SerialName("save_to_file") SAVE_TO_FILE
}

@Serializable
data class StepParams(
    val x: Int? = null,
    val y: Int? = null,
    @SerialName("to_x") val toX: Int? = null,
    @SerialName("to_y") val toY: Int? = null,
    val duration: Long? = null,
    val text: String? = null,
    val key: String? = null,
    @SerialName("app_package") val appPackage: String? = null,
    val direction: SwipeDirection? = null,
    val distance: Int? = null,
    val count: Int? = null,
    val scale: Float? = null,
    @SerialName("timeout_ms") val timeoutMs: Long? = null,
    @SerialName("file_path") val filePath: String? = null,
    val variable: String? = null
)

@Serializable
enum class SwipeDirection {
    @SerialName("up") UP,
    @SerialName("down") DOWN,
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT
}

@Serializable
data class ElementSelector(
    val text: String? = null,
    @SerialName("content_description") val contentDescription: String? = null,
    @SerialName("class_name") val className: String? = null,
    @SerialName("resource_id") val resourceId: String? = null,
    val index: Int? = null
)

@Serializable
enum class ErrorPolicy {
    @SerialName("stop") STOP,
    @SerialName("skip") SKIP,
    @SerialName("retry") RETRY
}
