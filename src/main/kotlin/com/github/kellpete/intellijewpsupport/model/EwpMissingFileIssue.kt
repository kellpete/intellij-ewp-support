package com.github.kellpete.intellijewpsupport.model

data class EwpMissingFileIssue(
    val groupPath: List<String>,
    val rawPath: String,
    val resolvedPath: String?,
)
