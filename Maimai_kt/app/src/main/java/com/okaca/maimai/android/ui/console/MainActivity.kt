package com.okaca.maimai.android.ui.console

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.okaca.maimai.android.R
import com.okaca.maimai.android.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var techFragment: TechFragment
    private lateinit var divingFishUploadFragment: DivingFishUploadFragment
    private var selectedTabId = R.id.tab_tech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedTabId = savedInstanceState?.getInt(KEY_SELECTED_TAB) ?: R.id.tab_tech
        initFragments()
        setupBottomTabs()
        handleIntentAction(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentAction(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::techFragment.isInitialized) {
            techFragment.onHostWindowFocusChanged(hasFocus)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_TAB, selectedTabId)
        super.onSaveInstanceState(outState)
    }

    fun showDivingFishUploadTab() {
        binding.mainBottomNavigation.selectedItemId = R.id.tab_diving_fish_upload
    }

    private fun initFragments() {
        techFragment = supportFragmentManager.findFragmentByTag(TechFragment.TAG)
                as? TechFragment ?: TechFragment.newInstance()
        divingFishUploadFragment =
            supportFragmentManager.findFragmentByTag(DivingFishUploadFragment.TAG)
                    as? DivingFishUploadFragment ?: DivingFishUploadFragment.newInstance()

        val transaction = supportFragmentManager.beginTransaction()
        if (!techFragment.isAdded) {
            transaction.add(R.id.mainFragmentContainer, techFragment, TechFragment.TAG)
        }
        if (!divingFishUploadFragment.isAdded) {
            transaction.add(
                R.id.mainFragmentContainer,
                divingFishUploadFragment,
                DivingFishUploadFragment.TAG,
            )
        }
        transaction.hide(techFragment)
        transaction.hide(divingFishUploadFragment)
        transaction.show(fragmentForTab(selectedTabId))
        transaction.commitNow()
    }

    private fun setupBottomTabs() {
        binding.mainBottomNavigation.setOnItemSelectedListener { item ->
            selectedTabId = item.itemId
            showFragment(fragmentForTab(item.itemId))
            true
        }
        binding.mainBottomNavigation.selectedItemId = selectedTabId
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .hide(techFragment)
            .hide(divingFishUploadFragment)
            .show(fragment)
            .commit()
    }

    private fun fragmentForTab(tabId: Int): Fragment =
        if (tabId == R.id.tab_diving_fish_upload) {
            divingFishUploadFragment
        } else {
            techFragment
        }

    private fun handleIntentAction(intent: Intent?) {
        techFragment.handleNewIntent(intent ?: return)
    }

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab"
    }
}
