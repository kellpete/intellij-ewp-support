package com.github.kellpete.intellijewpsupport.ewp.formatter

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import com.intellij.psi.formatter.common.AbstractBlock

class EwpFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        return FormattingModelProvider.createFormattingModelForPsiFile(
            formattingContext.containingFile,
            EwpRootBlock(formattingContext.node),
            formattingContext.codeStyleSettings,
        )
    }

    private class EwpRootBlock(node: ASTNode) : AbstractBlock(
        node,
        Wrap.createWrap(WrapType.NONE, false),
        Alignment.createAlignment(),
    ) {
        override fun buildChildren(): List<Block> = emptyList()

        override fun isLeaf(): Boolean = true

        override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

        override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
            ChildAttributes(Indent.getNoneIndent(), Alignment.createAlignment())

        override fun isIncomplete(): Boolean = false
    }
}
