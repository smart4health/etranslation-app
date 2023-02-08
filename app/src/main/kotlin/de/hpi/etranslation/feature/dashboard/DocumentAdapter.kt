package de.hpi.etranslation.feature.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.viewbinding.ViewBinding
import com.google.android.material.card.MaterialCardView
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.Lang
import de.hpi.etranslation.R
import de.hpi.etranslation.ViewBindingViewHolder
import de.hpi.etranslation.asFlag
import de.hpi.etranslation.databinding.ItemDashboardDocumentBinding
import de.hpi.etranslation.feature.dashboard.usecase.InferDocumentLanguageUseCase
import de.hpi.etranslation.feature.dashboard.viewmodel.DocumentsViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DocumentAdapter(
    private val itemClickCallback: (
        String,
        DocumentsViewModel.Action,
    ) -> Unit,
) : ListAdapter<DocumentsViewModel.Document, ViewBindingViewHolder<ItemDashboardDocumentBinding>>(
    DIFF_CALLBACK,
) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewBindingViewHolder<ItemDashboardDocumentBinding> {
        return LayoutInflater.from(parent.context)
            .let { ItemDashboardDocumentBinding.inflate(it, parent, false) }
            .let(::ViewBindingViewHolder)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(
        holder: ViewBindingViewHolder<ItemDashboardDocumentBinding>,
        position: Int,
    ) {
        val item = getItem(position)

        with(holder.binding) {
            title.text = item.title
            subtitle.text = item.resourceType

            val accountTypeLogoResId = when (item.accountType) {
                AccountType.S4H -> R.drawable.ic_s4h_logo_reduction
            }

            accountTypeLogo.setImageResource(accountTypeLogoResId)

            val asterisk = when (item.originalLangSource) {
                InferDocumentLanguageUseCase.Source.LOCAL_OVERRIDE -> ""
                InferDocumentLanguageUseCase.Source.GLOBAL_OVERRIDE -> ""
                InferDocumentLanguageUseCase.Source.LOCALE ->
                    context.getString(R.string.lang_source_asterisk_device)
                InferDocumentLanguageUseCase.Source.FALLBACK ->
                    context.getString(R.string.lang_source_asterisk_fallback)
            }

            flagTextView.text = if (item.translations.isEmpty()) {
                context.getString(
                    R.string.item_dashboard_document_subtitle_flags_without_translations,
                    item.originalLang.asFlag(),
                    asterisk,
                )
            } else {
                val translationFlags = item.translations
                    .sorted()
                    .joinToString(separator = " ", transform = Lang::asFlag)

                context.getString(
                    R.string.item_dashboard_document_subtitle_flags_with_translations,
                    item.originalLang.asFlag(),
                    asterisk,
                    translationFlags,
                )
            }

            sourceTextView.visibility =
                if (
                    item.originalLangSource != InferDocumentLanguageUseCase.Source.LOCAL_OVERRIDE &&
                    item.selectionState == DocumentsViewModel.SelectionState.EXPANDED
                ) View.VISIBLE else View.GONE

            sourceTextView.text = when (item.originalLangSource) {
                InferDocumentLanguageUseCase.Source.LOCAL_OVERRIDE -> ""
                InferDocumentLanguageUseCase.Source.GLOBAL_OVERRIDE -> context.getString(R.string.lang_source_global)
                InferDocumentLanguageUseCase.Source.LOCALE -> context.getString(
                    R.string.lang_source,
                    context.getString(R.string.lang_source_asterisk_device),
                    context.getString(R.string.lang_source_device),
                )
                InferDocumentLanguageUseCase.Source.FALLBACK -> context.getString(
                    R.string.lang_source,
                    context.getString(R.string.lang_source_asterisk_fallback),
                    context.getString(R.string.lang_source_fallback),
                )
            }

            holder.binding.inProgressTextView.text = if (item.inProgress.isNotEmpty()) {
                val flags = item.inProgress.joinToString(separator = " ", transform = Lang::asFlag)
                context.getString(R.string.item_dashboard_document_body_in_progress, flags)
            } else ""

            holder.binding.date.text = DateTimeFormatter.ofPattern("dd MMM yyyy")
                .withZone(ZoneId.systemDefault())
                .format(item.date)

            holder.binding.buttonFlow.isVisible =
                item.selectionState == DocumentsViewModel.SelectionState.EXPANDED

            viewButton.setOnClickListener {
                itemClickCallback(item.originalRecordId, DocumentsViewModel.Action.VIEW)
            }

            overrideButton.setOnClickListener {
                itemClickCallback(
                    item.originalRecordId,
                    DocumentsViewModel.Action.OVERRIDE_LANGUAGE,
                )
            }

            root.safeChecked = item.selectionState in listOf(
                DocumentsViewModel.SelectionState.SELECTED,
                DocumentsViewModel.SelectionState.EXPANDED,
            )

            uncheckedIndicator.visibility =
                if (item.selectionState == DocumentsViewModel.SelectionState.ENABLED && item.inProgress.isEmpty())
                    View.VISIBLE
                else View.INVISIBLE

            checkedIndicator.visibility =
                if (item.selectionState in listOf(
                        DocumentsViewModel.SelectionState.SELECTED,
                        DocumentsViewModel.SelectionState.EXPANDED,
                    ) && item.inProgress.isEmpty()
                )
                    View.VISIBLE
                else
                    View.INVISIBLE

            circularProgressIndicator.visibility =
                if (item.selectionState == DocumentsViewModel.SelectionState.PROCESSING || item.inProgress.isNotEmpty())
                    View.VISIBLE
                else View.INVISIBLE

            root.isEnabled =
                item.selectionState != DocumentsViewModel.SelectionState.PROCESSING

            root.setOnClickListener {
                itemClickCallback(item.originalRecordId, DocumentsViewModel.Action.CLICKED)
            }

            root.setOnLongClickListener {
                itemClickCallback(item.originalRecordId, DocumentsViewModel.Action.LONG_CLICKED)
                true
            }
        }
    }

    @Suppress("ClassName")
    object DIFF_CALLBACK : DiffUtil.ItemCallback<DocumentsViewModel.Document>() {
        override fun areItemsTheSame(
            oldItem: DocumentsViewModel.Document,
            newItem: DocumentsViewModel.Document,
        ) = oldItem.originalRecordId == newItem.originalRecordId

        override fun areContentsTheSame(
            oldItem: DocumentsViewModel.Document,
            newItem: DocumentsViewModel.Document,
        ) = oldItem == newItem
    }
}

/**
 * MaterialCardView only allows setting the checked state when it is enabled,
 * which can cause problems when recycling views: if the recycled view is checked,
 * but the item is disabled, it will disable first, and then be unable to uncheck,
 * if isEnabled is set before isChecked
 *
 * if they are set the other way around, then a view that was disabled cannot be
 * checked, since enabling happens later
 */
internal var MaterialCardView.safeChecked: Boolean
    get() = isChecked
    set(value) {
        val enabled = isEnabled
        isEnabled = true
        isChecked = value
        isEnabled = enabled
    }

val ViewBinding.context: Context
    get() = root.context
