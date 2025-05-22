package com.example.t4

import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.t4.databinding.ActivityMainBinding
import android.util.Log

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
//    private val TAG = "TTT4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置 Edge-to-Edge 布局
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 安全获取 NavHostFragment
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        if (navHostFragment is NavHostFragment) {
            val navController = navHostFragment.navController
            
            // 设置顶级目的地，这样在这些目的地之间导航时不会创建返回栈
            appBarConfiguration = AppBarConfiguration(
                setOf(R.id.homeFragment, R.id.bleScanFragment, R.id.bleDebugFragment)
            )
            
            setSupportActionBar(binding.topAppBar)
            setupActionBarWithNavController(navController, appBarConfiguration)
            
            // 设置底部导航栏与导航控制器的关联
            binding.bottomNavigation.setupWithNavController(navController)
            
            // 添加导航监听器来控制界面元素的可见性
            navController.addOnDestinationChangedListener { _, destination, _ ->
                // 在图像编辑界面隐藏底部导航栏
                if (destination.id == R.id.imageEditFragment) {
                    binding.bottomNavigation.visibility = View.GONE
                } else {
                    binding.bottomNavigation.visibility = View.VISIBLE
                }
            }
        } else {
            // 处理 NavHostFragment 不存在的情况
            Log.e("MainActivity", "NavHostFragment 未找到或类型不匹配")
        }
        
        // 处理系统 UI 区域
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                
                // 为顶部和底部添加内边距
                view.setPadding(0, insets.top, 0, insets.bottom)
                
                WindowInsetsCompat.CONSUMED
            }
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
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        return if (navHostFragment is NavHostFragment) {
            val navController = navHostFragment.navController
            navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
        } else {
            super.onSupportNavigateUp()
        }
    }
}
