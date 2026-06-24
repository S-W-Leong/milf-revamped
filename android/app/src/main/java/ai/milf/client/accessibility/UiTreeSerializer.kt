package ai.milf.client.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object UiTreeSerializer {
    fun serialize(
        root: AccessibilityNodeInfo?,
        screenWidth: Int,
        screenHeight: Int
    ): Map<String, Any?> {
        return mapOf(
            "a11y_tree" to (root?.let(::nodeToMap) ?: emptyRoot()),
            "phone_state" to phoneState(root),
            "device_context" to mapOf(
                "screen_bounds" to mapOf(
                    "width" to screenWidth,
                    "height" to screenHeight
                ),
                "filtering_params" to mapOf(
                    "min_element_size" to 5,
                    "overlay_offset" to 0
                )
            )
        )
    }

    private fun nodeToMap(node: AccessibilityNodeInfo): Map<String, Any?> {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val children = mutableListOf<Map<String, Any?>>()
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                try {
                    children += nodeToMap(child)
                } finally {
                    child.recycle()
                }
            }
        }

        return mapOf(
            "boundsInScreen" to boundsMap(bounds),
            "children" to children,
            "className" to node.className.asString(),
            "contentDescription" to node.contentDescription.asString(),
            "isCheckable" to node.isCheckable,
            "isChecked" to node.isChecked,
            "isClickable" to node.isClickable,
            "isEnabled" to node.isEnabled,
            "isFocusable" to node.isFocusable,
            "isFocused" to node.isFocused,
            "isLongClickable" to node.isLongClickable,
            "isPassword" to node.isPassword,
            "isScrollable" to node.isScrollable,
            "isSelected" to node.isSelected,
            "packageName" to node.packageName.asString(),
            "resourceId" to node.viewIdResourceName.orEmpty(),
            "text" to node.text.asString()
        )
    }

    private fun phoneState(root: AccessibilityNodeInfo?): Map<String, Any?> {
        val packageName = root?.packageName.asString()
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        return try {
            mapOf(
                "currentApp" to packageName,
                "packageName" to packageName,
                "activityName" to "",
                "keyboardVisible" to (focused?.isEditable == true),
                "isEditable" to (focused?.isEditable == true),
                "focusedElement" to focused?.let {
                    mapOf(
                        "className" to it.className.asString(),
                        "resourceId" to it.viewIdResourceName.orEmpty(),
                        "text" to it.text.asString()
                    )
                }
            )
        } finally {
            if (focused != null && focused !== root) {
                focused.recycle()
            }
        }
    }

    private fun emptyRoot(): Map<String, Any?> =
        mapOf(
            "boundsInScreen" to mapOf(
                "left" to 0,
                "top" to 0,
                "right" to 0,
                "bottom" to 0
            ),
            "children" to emptyList<Map<String, Any?>>(),
            "className" to "android.view.View",
            "contentDescription" to "",
            "isCheckable" to false,
            "isChecked" to false,
            "isClickable" to false,
            "isEnabled" to true,
            "isFocusable" to false,
            "isFocused" to false,
            "isLongClickable" to false,
            "isPassword" to false,
            "isScrollable" to false,
            "isSelected" to false,
            "packageName" to "",
            "resourceId" to "",
            "text" to ""
        )

    private fun boundsMap(bounds: Rect): Map<String, Int> =
        mapOf(
            "left" to bounds.left,
            "top" to bounds.top,
            "right" to bounds.right,
            "bottom" to bounds.bottom
        )

    private fun CharSequence?.asString(): String {
        return this?.toString().orEmpty()
    }
}
