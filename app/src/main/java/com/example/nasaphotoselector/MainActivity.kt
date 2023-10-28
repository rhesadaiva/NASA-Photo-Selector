package com.example.nasaphotoselector

import android.annotation.SuppressLint
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import androidx.navigation.compose.rememberNavController
import java.net.URL

@Serializable
data class NASAData(
    @SerialName("title") val title: String,
    @SerialName("url") val url: String
)

sealed class Screen(val route: String, val label: String) {
    object Photos : Screen("photos", "Photos")
    object PhotoRank : Screen("favorites", "Favorites")
}

enum class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    PHOTOS(Screen.Photos.route, Icons.Default.Face, "Photos"),
    PHOTO_RANK(Screen.PhotoRank.route, Icons.Default.Star, "Favorites");
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NASAImageList(nasaDataList: List<NASAData>?, viewModel: FavoritesViewModel) {
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(nasaDataList ?: emptyList()) { data ->
            Log.d("NASAImageList", "Loading image from URL: ${data.url}")
            val painter = rememberAsyncImagePainter(data.url)

            val coroutineScope = rememberCoroutineScope()
            var isLongPressed by remember { mutableStateOf(false) }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(true,
                        onClick = {
                            coroutineScope.launch {
                                Toast
                                    .makeText(
                                        context,
                                        "Hold to Save it to Favorites",
                                        Toast.LENGTH_LONG
                                    )
                                    .show()
                            }
                        },
                        onDoubleClick = {
                            coroutineScope.launch {
                                viewModel.insertFavorite(data.title, data.url)
                                Toast
                                    .makeText(context, "Added to Favorites", Toast.LENGTH_SHORT)
                                    .show()
                                Log.d(
                                    "Add Favorite",
                                    "Menambah data ${data.title} dengan url ${data.url}"
                                )
                            }
                        }
                    )
                    .padding(16.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .background(Color.Gray)
                        .height(250.dp)
                        .fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = data.title,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Start),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMainScreen() {
    val navController = rememberNavController()
    val currentRoute = currentRoute(navController)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                ),
                title = {
                    Text("NASA Images", fontWeight = FontWeight.Bold)
                }
            )
        },
        bottomBar = {
            BottomNavigation(
                backgroundColor = Color(247, 247, 247),
            ) {
                BottomNavItem.values().forEach { item ->
                    val isSelected = currentRoute == item.label
//                    Log.d("Is selected: $isSelected")
                    Log.d("status is selected", isSelected.toString())
                    BottomNavigationItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
//                                tint = if (isSelected) Color.White else Color.Gray // Change as per your requirements
                                tint = Color.Black
                            )
                        },
                        label = { Text(item.label, fontWeight = FontWeight.Bold, color = Color.Black) },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(item.label) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Screen.Photos.route) {
            composable(Screen.Photos.route) {
                PhotosPage()
            }
            composable(Screen.PhotoRank.route) {
                FavoritesScreen()
            }
        }
    }
}

@Composable
fun currentRoute(navController: NavHostController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}

suspend fun fetchAndSetData(nasaDataList: MutableState<List<NASAData>?>, isRefreshing: MutableState<Boolean>) {
    isRefreshing.value = true
    val data = fetchNASAData()
    nasaDataList.value = data
    isRefreshing.value = false
}

suspend fun fetchNASAData(): List<NASAData>? = withContext(Dispatchers.IO) {
    val url =
        "https://api.nasa.gov/planetary/apod?date=&count=10&thumbs=null&api_key=2AAlN2XUY0FCXLoRDfj9cv43BBJvCeHexPiVJ5Y5"
    val result = URL(url).readText()
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }  // <-- Adjusted here
    val data = json.decodeFromString(ListSerializer(NASAData.serializer()), result)
    data
}

/**
 * Favorites Page
 */
@Composable
fun FavoritesScreen() {
    val context = LocalContext.current
    val viewModel: FavoritesViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            return FavoritesViewModel(context.applicationContext as Application) as T
        }
    })

    val favoritesList by viewModel.allFavorites.observeAsState(initial = emptyList())

   Column(
       modifier = Modifier
           .padding(3.dp)
           .padding(top = 56.dp, bottom = 56.dp),
       verticalArrangement = Arrangement.spacedBy(8.dp)
   ) {
       LazyColumn(
           modifier = Modifier.fillMaxSize(),
           verticalArrangement = Arrangement.spacedBy(8.dp)
       ) {
           items(favoritesList) { favorite ->
               Log.d("Favorite Item List",favorite.toString())
               FavoriteItem(favorite = favorite, onDelete = { viewModel.deleteFavorite(it) })
           }
       }
   }
}

@Composable
fun FavoriteItem(favorite: Favorite,onDelete: (Favorite) -> Unit) {
    val painter = rememberAsyncImagePainter(favorite.url)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .doubleClickOnTap(favorite.title) { onDelete(favorite) }

    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .background(Color.Gray)
                .height(250.dp)
                .fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = favorite.title,
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.Start),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun Modifier.doubleClickOnTap( title: String,onDoubleClick: () -> Unit): Modifier {
    val context = LocalContext.current
    return pointerInput(Unit) {
        detectTapGestures(onDoubleTap = {
            onDoubleClick()
            Toast.makeText(
                context,
                "Successfully removed from Favorites",
                Toast.LENGTH_SHORT
            ).show()
        })
    }
}

@Composable
fun PhotosPage() {
    val nasaDataListState = remember { mutableStateOf<List<NASAData>?>(null) }
    val isRefreshingState = remember { mutableStateOf(false) }

    // Use `by` keyword for easy access
    var nasaDataList by nasaDataListState
    var isRefreshing by isRefreshingState

    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current

    val viewModel: FavoritesViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            return FavoritesViewModel(context.applicationContext as Application) as T

        }
    })

    LaunchedEffect(Unit) {
        fetchAndSetData(nasaDataListState, isRefreshingState)
    }

    Column(
        modifier = Modifier
            .padding(3.dp)
            .padding(top = 56.dp, bottom = 56.dp), // Adjust for both top and bottom bars
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            com.google.accompanist.swiperefresh.SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
                onRefresh = {
                    coroutineScope.launch {
                        fetchAndSetData(nasaDataListState, isRefreshingState)
                    }
                },
                indicator = { state, trigger ->
                    com.google.accompanist.swiperefresh.SwipeRefreshIndicator(
                        state = state,
                        refreshTriggerDistance = trigger,
                        scale = true,
                        backgroundColor = Color.White,
                        contentColor = Color.Black
                    )
                }
            ) {
                NASAImageList(nasaDataList,viewModel)
            }
        }
    }
}

@Composable
fun SplashScreen() {
    val image = painterResource(id = R.drawable.nasa3)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = image,
                contentDescription = "Logo",
                modifier = Modifier.padding(16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))  // Adds space between Image and Text
            Text("NASA Image Collection Gallery")
        }

        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp)
        )
    }
}


@Composable
fun MainAppContent() {
    val showSplash = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2000)
        showSplash.value = false
    }

    Box {
        // Show main app content only if splash screen is not visible
        if (!showSplash.value) {
            AppMainScreen()
        } else {
            SplashScreen()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Your main app UI
            MainAppContent()
        }
    }
}
