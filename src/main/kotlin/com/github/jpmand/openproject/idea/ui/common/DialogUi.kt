package com.github.jpmand.openproject.idea.ui.common

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Where coroutines of a modal dialog update the dialog's own components: the EDT in any modality.
 * [Dispatchers.EDT] without a modality in the context runs only when no modal dialog is shown, i.e. after the
 * dialog was closed. Only for pure UI updates, in a scope that is cancelled when the dialog is disposed.
 */
val DIALOG_UI: CoroutineContext get() = Dispatchers.EDT + ModalityState.any().asContextElement()
