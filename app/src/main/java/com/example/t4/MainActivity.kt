package com.example.t4

import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.example.t4.databinding.ActivityMainBinding
import androidx.navigation.fragment.NavHostFragment
import android.view.View
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import kotlin.math.log

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
//    private val TAG = "TTT4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
//        binding.toolbar.visibility = View.VISIBLE
//        binding.bottomNavigation.visibility = View.VISIBLE
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // 设置底部导航栏的点击事件
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    navController.navigate(R.id.homeFragment)
                    true
                }
                R.id.navigation_ble -> {
                    navController.navigate(R.id.bleScanFragment)
                    true
                }
                R.id.navigation_image -> {
                    navController.navigate(R.id.imageEditFragment)
                    true
                }
                R.id.navigation_debug -> {
                    navController.navigate(R.id.bleDebugFragment)
                    true
                }
                else -> false
            }
        }

//        // 添加导航监听器，根据当前页面控制Toolbar的可见性
//        navController.addOnDestinationChangedListener { _, destination, _ ->
//            when (destination.id) {
//                R.id.homeFragment -> {
//                    // 在主页显示Toolbar
//                    supportActionBar?.show()
//                    binding.toolbar.visibility = View.VISIBLE
//                    binding.bottomNavigation.visibility = View.VISIBLE
//                }
//                else -> {
//                    // 在其他页面隐藏Toolbar，但保留底部导航栏
//                    supportActionBar?.hide()
//                    binding.toolbar.visibility = View.GONE
//                    binding.bottomNavigation.visibility = View.VISIBLE
//                }
//            }
//        }

//        Log.d(TAG, "onCreate: 6789876!!")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
//        Log.d(TAG, "onSupportNavigateUp: MainA")
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}