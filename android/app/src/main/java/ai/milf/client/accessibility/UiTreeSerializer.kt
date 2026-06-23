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
        val text = node.text?.toString()
        val contentDescription = node.contentDescription?.toString()
        val viewIdResourceName = node.viewIdResourceName
        val clickable = node.isClickable
        val enabled = node.isEnabled
        val focused = node.isFocused
        val selected = node.isSelected

        if (!isEmptyDisabledLeaf(
                text = text,
                contentDescription = contentDescription,
                viewIdResourceName = viewIdResourceName,
                clickable = clickable,
                enabled = enabled,
                focused = focused,
                selected = selected,
                childCount = node.childCount
            )
        ) {
            nodes += mapOf(
                "id" to id,
                "text" to text,
                "contentDescription" to contentDescription,
                "viewIdResourceName" to viewIdResourceName,
                "className" to node.className?.toString(),
                "packageName" to node.packageName?.toString(),
                "clickable" to clickable,
                "enabled" to enabled,
                "focused" to focused,
                "selected" to selected,
                "bounds" to mapOf(
                    "left" to bounds.left,
                    "top" to bounds.top,
                    "right" to bounds.right,
                    "bottom" to bounds.bottom
                )
            )
        }

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

    private fun isEmptyDisabledLeaf(
        text: String?,
        contentDescription: String?,
        viewIdResourceName: String?,
        clickable: Boolean,
        enabled: Boolean,
        focused: Boolean,
        selected: Boolean,
        childCount: Int
    ): Boolean =
        childCount == 0 &&
            !enabled &&
            !clickable &&
            !focused &&
            !selected &&
            text.isNullOrBlank() &&
            contentDescription.isNullOrBlank() &&
            viewIdResourceName.isNullOrBlank()
}
