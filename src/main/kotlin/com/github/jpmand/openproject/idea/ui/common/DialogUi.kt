package com.github.jpmand.openproject.idea.ui.common

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Where coroutines of a modal dialog update the dialog's own components: the EDT in any modality.
 * Without a modality in the context, the EDT dispatchers run only when no modal dialog is shown, i.e. after the
 * dialog was closed. Only for pure UI updates, in a scope that is cancelled when the dialog is disposed:
 * [Dispatchers.UI] takes no write-intent lock and reports read or write actions inside it as errors.
 */
val DIALOG_UI: CoroutineContext get() = Dispatchers.UI + ModalityState.any().asContextElement()
