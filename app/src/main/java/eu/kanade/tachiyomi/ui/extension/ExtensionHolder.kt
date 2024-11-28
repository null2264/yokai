package eu.kanade.tachiyomi.ui.extension

import android.animation.AnimatorInflater
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.scale
import androidx.core.view.isGone
import androidx.core.view.isVisible
import coil3.dispose
import coil3.load
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.CoverViewTarget
import eu.kanade.tachiyomi.databinding.ExtensionCardItemBinding
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import eu.kanade.tachiyomi.util.view.resetStrokeColor
import eu.kanade.tachiyomi.util.view.setText
import yokai.i18n.MR
import yokai.util.lang.getString
import java.util.*
import android.R as AR

class ExtensionHolder(view: View, val adapter: ExtensionAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    private val context = view.context
    private val binding = ExtensionCardItemBinding.bind(view)
    init {
        binding.extButton.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(flexibleAdapterPosition)
        }
        binding.cancelButton.setOnClickListener {
            adapter.buttonClickListener.onCancelClick(flexibleAdapterPosition)
        }
    }

    fun bind(item: ExtensionItem) {
        val extension = item.extension

        // Set source name

        val infoText = mutableListOf(extension.versionName)
        binding.date.isVisible = false
        if (extension is Extension.Installed && !extension.hasUpdate) {
            when (InstalledExtensionsOrder.fromValue(adapter.installedSortOrder)) {
                InstalledExtensionsOrder.RecentlyUpdated -> {
                    ExtensionLoader.extensionUpdateDate(itemView.context, extension)
                        .takeUnless { it == 0L }?.let {
                            binding.date.isVisible = true
                            binding.date.text = itemView.context.timeSpanFromNow(MR.strings.updated_, it)
                            infoText.add("")
                        }
                }
                InstalledExtensionsOrder.RecentlyInstalled -> {
                    ExtensionLoader.extensionInstallDate(itemView.context, extension)
                        .takeUnless { it == 0L }?.let {
                            binding.date.isVisible = true
                            binding.date.text =
                                itemView.context.timeSpanFromNow(
                                    if (extension.isShared) {
                                        MR.strings.installed_
                                    } else {
                                        MR.strings.added_
                                    },
                                    it,
                                )
                            infoText.add("")
                        }
                }
                else -> binding.date.isVisible = false
            }
        } else {
            binding.date.isVisible = false
        }
        binding.lang.isVisible = binding.date.isGone
        binding.extTitle.text = if (infoText.size > 1) {
            buildSpannedString {
                append(extension.name + " ")
                color(binding.extTitle.context.getResourceColor(AR.attr.textColorSecondary)) {
                    scale(0.75f) {
                        append(LocaleHelper.getLocalizedDisplayName(extension.lang))
                    }
                }
            }
        } else {
            extension.name
        }

        binding.version.text = infoText.joinToString(" • ")
        binding.lang.text = LocaleHelper.getLocalizedDisplayName(extension.lang)
        binding.warning.text = when {
            extension.isNsfw -> itemView.context.getString(MR.strings.nsfw_short)
            else -> ""
        }.plusRepo(extension).uppercase(Locale.ROOT)
        binding.installProgress.progress = item.sessionProgress ?: 0
        binding.installProgress.isVisible = item.sessionProgress != null
        binding.cancelButton.isVisible = item.sessionProgress != null

        binding.sourceImage.dispose()

        when (extension) {
            is Extension.Available -> {
                binding.sourceImage.load(extension.iconUrl) {
                    target(CoverViewTarget(binding.sourceImage))
                }
            }
            is Extension.Installed -> {
                binding.sourceImage.load(extension.icon)
            }
            is Extension.Untrusted -> {
                binding.sourceImage.setImageDrawable(
                    context.contextCompatDrawable(R.drawable.ic_report_24dp)
                )
            }
        }
        bindButton(item)
    }

    private fun String.plusRepo(extension: Extension): String {
        val repoText = when {
            extension is Extension.Untrusted -> itemView.context.getString(MR.strings.untrusted)
            extension is Extension.Installed && extension.isObsolete -> itemView.context.getString(MR.strings.obsolete)
            else -> ""
        }

        if (repoText.isEmpty()) return this

        return if (isEmpty()) {
            this
        } else {
            "$this • "
        } + repoText
    }

    @Suppress("ResourceType")
    fun bindButton(item: ExtensionItem) = with(binding.extButton) {
        if (item.installStep == InstallStep.Done) return@with
        isEnabled = true
        isClickable = true
        isActivated = false

        binding.installProgress.progress = item.sessionProgress ?: 0
        binding.cancelButton.isVisible = item.sessionProgress != null
        binding.installProgress.isVisible = item.sessionProgress != null
        val extension = item.extension
        val installStep = item.installStep
        strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
        rippleColor = ColorStateList.valueOf(context.getResourceColor(R.attr.colorControlHighlight))
        stateListAnimator = null
        if (installStep != null) {
            setText(
                when (installStep) {
                    InstallStep.Pending -> MR.strings.pending
                    InstallStep.Downloading -> MR.strings.downloading
                    InstallStep.Loading -> MR.strings.loading
                    InstallStep.Installing -> MR.strings.installing
                    InstallStep.Installed -> MR.strings.installed
                    InstallStep.Error -> MR.strings.retry
                    else -> return@with
                },
            )
            if (installStep != InstallStep.Error) {
                isEnabled = false
                isClickable = false
            }
        } else if (extension is Extension.Installed) {
            when {
                extension.hasUpdate -> {
                    isActivated = true
                    stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.icon_btn_state_list_anim)
                    rippleColor = ColorStateList.valueOf(context.getColor(R.color.on_secondary_highlight))
                    setText(MR.strings.update)
                }
                else -> {
                    setText(MR.strings.settings)
                }
            }
        } else if (extension is Extension.Untrusted) {
            resetStrokeColor()
            setText(MR.strings.trust)
        } else {
            resetStrokeColor()
            setText(if (adapter.installPrivately) MR.strings.add else MR.strings.install)
        }
    }
}
