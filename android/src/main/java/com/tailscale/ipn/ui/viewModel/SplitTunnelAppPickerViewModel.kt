// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.App
import com.tailscale.ipn.mdm.MDMSettings
import com.tailscale.ipn.mdm.SettingState
import com.tailscale.ipn.ui.util.InstalledApp
import com.tailscale.ipn.ui.util.InstalledAppsManager
import com.tailscale.ipn.ui.util.set
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SplitTunnelAppPickerViewModel : ViewModel() {
  val installedAppsManager = InstalledAppsManager(packageManager = App.get().packageManager)
  val excludedPackageNames: StateFlow<List<String>> = MutableStateFlow(listOf())
  val installedApps: StateFlow<List<InstalledApp>> = MutableStateFlow(listOf())
  val filteredApps: StateFlow<List<InstalledApp>> = MutableStateFlow(listOf())
  val searchTerm: StateFlow<String> = MutableStateFlow("")
  val mdmExcludedPackages: StateFlow<SettingState<String?>> = MDMSettings.excludedPackages.flow
  val mdmIncludedPackages: StateFlow<SettingState<String?>> = MDMSettings.includedPackages.flow

  private var saveJob: Job? = null

  init {
    installedApps.set(installedAppsManager.fetchInstalledApps())
    excludedPackageNames.set(
        App.get()
            .disallowedPackageNames()
            .intersect(installedApps.value.map { it.packageName }.toSet())
            .toList())
    filteredApps.set(installedApps.value)

    viewModelScope.launch {
      searchTerm
          .debounce(200)
          .distinctUntilChanged()
          .combine(installedApps) { term, apps ->
            val trimmed = term.trim()
            if (trimmed.isEmpty()) {
              apps
            } else {
              apps.filter { it.name.contains(trimmed, ignoreCase = true) }
            }
          }
          .collect { filteredApps.set(it) }
    }
  }

  fun exclude(packageName: String) {
    if (excludedPackageNames.value.contains(packageName)) return
    excludedPackageNames.set(excludedPackageNames.value + packageName)
    debounceSave()
  }

  fun unexclude(packageName: String) {
    excludedPackageNames.set(excludedPackageNames.value - packageName)
    debounceSave()
  }

  fun updateSearchTerm(term: String) {
    searchTerm.set(term)
  }

  private fun debounceSave() {
    saveJob?.cancel()
    saveJob =
        viewModelScope.launch {
          delay(500) // Wait to batch multiple rapid updates
          App.get().updateUserDisallowedPackageNames(excludedPackageNames.value)
        }
  }
}
