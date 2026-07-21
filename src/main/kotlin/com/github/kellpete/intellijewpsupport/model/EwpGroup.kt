package com.github.kellpete.intellijewpsupport.model

data class EwpGroup(
    val name: String,
    val groups: List<EwpGroup>,
    val files: List<EwpFileEntry>,
)
