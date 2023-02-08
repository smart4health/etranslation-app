package de.hpi.etranslation.feature.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.R
import de.hpi.etranslation.ViewBindingViewHolder
import de.hpi.etranslation.databinding.ItemDashboardAccountsBinding
import de.hpi.etranslation.feature.dashboard.viewmodel.AccountsViewModel

class AccountsAdapter(
    private val itemClickCallback: (String, ItemAction) -> Unit,
) :
    ListAdapter<AccountsViewModel.AccountUiModel, ViewBindingViewHolder<ItemDashboardAccountsBinding>>(
        DIFF_CALLBACK,
    ) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewBindingViewHolder<ItemDashboardAccountsBinding> {
        return LayoutInflater.from(parent.context)
            .let { ItemDashboardAccountsBinding.inflate(it, parent, false) }
            .let(::ViewBindingViewHolder)
    }

    override fun onBindViewHolder(
        holder: ViewBindingViewHolder<ItemDashboardAccountsBinding>,
        position: Int,
    ) {
        val item = getItem(position)
        with(holder.binding) {
            val accountTypeIcon = when (item.accountType) {
                AccountType.S4H -> R.drawable.ic_s4h_logo_reduction
            }

            errorIcon.visibility = if (item.hasError) View.VISIBLE else View.INVISIBLE

            accountTypeLogo.setImageResource(accountTypeIcon)
            accountTypeLogo.contentDescription = when (item.accountType) {
                AccountType.S4H -> context.getString(R.string.item_dashboard_accounts_account_type_s4h_content_description)
            }

            spacer.visibility = if (position == itemCount - 1) View.VISIBLE else View.GONE

            cardView.setOnClickListener {
                itemClickCallback(item.id, ItemAction.CLICK)
            }
        }
    }

    @Suppress("ClassName")
    object DIFF_CALLBACK : DiffUtil.ItemCallback<AccountsViewModel.AccountUiModel>() {
        override fun areItemsTheSame(
            oldItem: AccountsViewModel.AccountUiModel,
            newItem: AccountsViewModel.AccountUiModel,
        ) = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: AccountsViewModel.AccountUiModel,
            newItem: AccountsViewModel.AccountUiModel,
        ) = oldItem == newItem
    }

    enum class ItemAction {
        CLICK,
    }
}
