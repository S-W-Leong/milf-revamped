package ai.milf.client.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object UiTreeSerializer {
    fun serialize(root: AccessibilityNodeInfo?): Map<String, Any?> {
        if (root == null) {
            return mapOf("nodes" to emptyList<Map<String, Any?>>())
        }

        val nodes = mutableListOf<Map<String, Any?>>()
        visit(root, id = "0", nodes = nodes)
        return mapOf("nodes" to nodes)
    }

    private fun visit(
        node: AccessibilityNodeInfo,
        id: String,
        nodes: MutableList<Map<String, Any?>>
    ) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        nodes += mapOf(
            "id" to id,
            "text" to node.text?.toString(),
            "contentDescription" to node.contentDescription?.toString(),
            "viewIdResourceName" to node.viewIdResourceName,
            "className" to node.className?.toString(),
            "packageName" to node.packageName?.toString(),
            "clickable" to node.isClickable,
            "enabled" to node.isEnabled,
            "focused" to node.isFocused,
            "selected" to node.isSelected,
            "bounds" to mapOf(
                "left" to bounds.left,
                "top" to bounds.top,
                "right" to bounds.right,
                "bottom" to bounds.bottom
            )
        )

        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                try {
                    visit(child, id = "$id.$index", nodes = nodes)
                } finally {
                    child.recycle()
                }
            }
        }
    }
}
