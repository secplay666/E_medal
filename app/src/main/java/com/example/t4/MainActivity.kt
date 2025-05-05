package com.example.t4

import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
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
        
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        // 设置顶级目的地，这样在这些目的地之间导航时不会创建返回栈
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.homeFragment, R.id.bleScanFragment, R.id.imageEditFragment, R.id.bleDebugFragment)
        )
        
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)
        
        // 可以添加导航监听器来控制界面元素的可见性
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // 根据需要控制界面元素的可见性
        }
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
