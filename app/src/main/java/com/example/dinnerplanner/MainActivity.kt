package com.example.dinnerplanner

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dinnerplanner.ui.theme.DinnerPlannerTheme
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.map
import com.google.firebase.firestore.PropertyName
import kotlinx.coroutines.flow.catch
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler

// Update Meal data class for Firestore
data class Meal(
    @DocumentId val id: String? = null,
    @PropertyName("name") val name: String = "",
    @PropertyName("checked") val isChecked: Boolean = false,
    @PropertyName("ingredients") val ingredients: List<String> = emptyList(),
    @PropertyName("recipe") val recipe: String = ""
) {
}

class MainActivity : ComponentActivity() {

    // Get Firestore instance
    private val db = Firebase.firestore
    private val mealsCollection = db.collection("meals")

    @OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DinnerPlannerTheme {

                var meals by remember { mutableStateOf<List<Meal>>(emptyList()) }

                DisposableEffect(Unit) {
                    val listenerRegistration = mealsCollection.addSnapshotListener(
                    ) { snapshot, error ->
                        if (error != null) {
                            Log.e("FirestoreManual", "Listen failed.", error)
                            return@addSnapshotListener
                        }

                        if (snapshot != null) {
                            val source = if (snapshot.metadata.isFromCache) "CACHE" else "SERVER"
                            Log.d("FirestoreManual", "Listener triggered... Source: $source, Size: ${snapshot.size()}")
                            val updatedMeals = snapshot.documents.mapNotNull { doc ->
                                try {
                                    val meal = doc.toObject<Meal>()
                                    if (meal != null) {
                                        Log.d("FirestoreMapping", "Doc ID: ${doc.id}, Name: ${meal.name}, isChecked: ${meal.isChecked}")
                                    } else {
                                        Log.w("FirestoreMapping", "Doc ID: ${doc.id} converted to null Meal object.")
                                    }
                                    meal
                                } catch (e: Exception) {
                                    Log.e("FirestoreManual", "Error converting document ${doc.id}", e)
                                    null
                                }
                            }
                            meals = updatedMeals
                            Log.d("FirestoreManual", "Local meals state updated. Count: ${updatedMeals.size}. First checked: ${updatedMeals.firstOrNull { it.isChecked }?.name ?: "None"}")
                        } else {
                            Log.d("FirestoreManual", "Snapshot was null")
                        }
                    }

                    onDispose {
                        listenerRegistration.remove()
                    }
                }

                var showMealDialog by remember { mutableStateOf(false) }
                var mealToEdit by remember { mutableStateOf<Meal?>(null) }
                val tabTitles = listOf("Available", "Meals", "Ingredients")
                var selectedMeal by remember { mutableStateOf<Meal?>(null) }

                val pagerState = rememberPagerState(initialPage = 1) { tabTitles.size }
                val scope = rememberCoroutineScope()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (selectedMeal != null && pagerState.currentPage == 1) {
                            TopAppBar(
                                title = { Text(selectedMeal!!.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                navigationIcon = {
                                    IconButton(onClick = { selectedMeal = null }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                }
                            )
                        } else {
                            TopAppBar(
                                title = { Text("Dinner Planner") },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                actions = {
                                    IconButton(onClick = {
                                        mealToEdit = null
                                        showMealDialog = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "Add Meal"
                                        )
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        if (selectedMeal == null || pagerState.currentPage != 1) {
                            TabRow(selectedTabIndex = pagerState.currentPage) {
                                tabTitles.forEachIndexed { index, title ->
                                    Tab(
                                        selected = pagerState.currentPage == index,
                                        enabled = !(selectedMeal != null && pagerState.currentPage == 1),
                                        onClick = {
                                            scope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                            selectedMeal = null
                                        },
                                        text = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize().weight(1f),
                            key = { it }
                        ) { page ->
                            val contentModifier = Modifier.fillMaxSize().padding(16.dp)
                            when (page) {
                                0 -> AvailableScreen(meals = meals, modifier = contentModifier)
                                1 -> {
                                    if (selectedMeal == null) {
                                        MealListScreen(
                                            meals = meals,
                                            onMealCheckedChange = { mealId, isChecked ->
                                                if (mealId != null) {
                                                    mealsCollection.document(mealId)
                                                        .update("checked", isChecked)
                                                        .addOnSuccessListener {
                                                            mealsCollection.document(mealId).get()
                                                                .addOnSuccessListener { docSnapshot ->
                                                                    if (docSnapshot.exists()) {
                                                                        val manualReadIsChecked = docSnapshot.getBoolean("checked")
                                                                        val currentMeals = meals
                                                                        val mealIndex = currentMeals.indexOfFirst { it.id == mealId }
                                                                        if (mealIndex != -1) {
                                                                            val updatedMeal = currentMeals[mealIndex].copy(isChecked = manualReadIsChecked ?: false)
                                                                            val newMealsList = currentMeals.toMutableList()
                                                                            newMealsList[mealIndex] = updatedMeal
                                                                            meals = newMealsList
                                                                        } else {
                                                                            Log.w("FirestoreManualRead", "Couldn't find meal $mealId in local state to update manually.")
                                                                        }
                                                                    } else {
                                                                        Log.w("FirestoreManualRead", "Manual re-read failed for ID $mealId: Document doesn't exist")
                                                                    }
                                                                }
                                                                .addOnFailureListener { e ->
                                                                    Log.e("FirestoreManualRead", "Manual re-read failed for ID $mealId", e)
                                                                }
                                                        }
                                                        .addOnFailureListener { e ->
                                                            Log.w("FirestoreCheck", "Update failed for ID: $mealId", e)
                                                        }
                                                } else {
                                                    Log.w("FirestoreCheck", "Checkbox change triggered with null mealId")
                                                }
                                            },
                                            onMealClick = { meal ->
                                                selectedMeal = meal
                                            },
                                            onMealEditClick = { meal ->
                                                mealToEdit = meal
                                                showMealDialog = true
                                            },
                                            modifier = contentModifier
                                        )
                                    } else {
                                        MealDetailScreen(
                                            meal = selectedMeal!!,
                                            modifier = contentModifier
                                        )
                                    }
                                }
                                2 -> IngredientsScreen(meals = meals, modifier = contentModifier)
                            }
                        }
                    }
                }

                // Global BackHandler to handle system back gestures when a meal is selected
                BackHandler(enabled = selectedMeal != null) {
                    selectedMeal = null
                }

                if (showMealDialog) {
                    MealDialog(
                        mealToEdit = mealToEdit,
                        onDismiss = {
                            showMealDialog = false
                            mealToEdit = null
                        },
                        onConfirmMeal = { mealData ->
                            if (mealData.name.isNotBlank()) {
                                if (mealData.id == null) {
                                    mealsCollection.add(mealData)
                                        .addOnSuccessListener { Log.d("Firestore", "Meal added") }
                                        .addOnFailureListener { e -> Log.w("Firestore", "Error adding meal", e) }
                                } else {
                                    mealsCollection.document(mealData.id)
                                        .set(mealData)
                                        .addOnSuccessListener { Log.d("Firestore", "Meal updated") }
                                        .addOnFailureListener { e -> Log.w("Firestore", "Error updating meal", e) }
                                }
                                if (selectedMeal?.id == mealData.id) {
                                    selectedMeal = mealData
                                }
                            }
                            showMealDialog = false
                            mealToEdit = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MealDialog(
    mealToEdit: Meal?,
    onDismiss: () -> Unit,
    onConfirmMeal: (meal: Meal) -> Unit
) {
    var mealName by remember { mutableStateOf("") }
    var ingredientsText by remember { mutableStateOf("") }
    var recipeText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(mealToEdit) {
        mealName = mealToEdit?.name ?: ""
        ingredientsText = mealToEdit?.ingredients?.joinToString(", ") ?: ""
        recipeText = mealToEdit?.recipe ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mealToEdit == null) "Add New Meal" else "Edit Meal") },
        text = {
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                OutlinedTextField(
                    value = mealName,
                    onValueChange = { mealName = it },
                    label = { Text("Meal Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = ingredientsText,
                    onValueChange = { ingredientsText = it },
                    label = { Text("Ingredients (comma-separated)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = recipeText,
                    onValueChange = { recipeText = it },
                    label = { Text("Recipe") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ingredientsList = ingredientsText
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    val meal = Meal(
                        id = mealToEdit?.id,
                        name = mealName,
                        ingredients = ingredientsList,
                        recipe = recipeText,
                        isChecked = mealToEdit?.isChecked ?: false
                    )
                    onConfirmMeal(meal)
                },
                enabled = mealName.isNotBlank()
            ) {
                Text(if (mealToEdit == null) "Add" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MealListScreen(
    meals: List<Meal>,
    onMealCheckedChange: (mealId: String?, isChecked: Boolean) -> Unit,
    onMealClick: (Meal) -> Unit,
    onMealEditClick: (Meal) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(meals, key = { it.id ?: it.hashCode() }) { meal ->
            MealItem(
                meal = meal,
                onCheckedChange = { isChecked -> onMealCheckedChange(meal.id, isChecked) },
                onClick = { onMealClick(meal) },
                onEditClick = { onMealEditClick(meal) }
            )
        }
    }
}

@Composable
fun MealItem(
    meal: Meal,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = meal.isChecked,
                onCheckedChange = onCheckedChange
            )
            Text(
                text = meal.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit Meal"
                )
            }
        }
    }
}

@Composable
fun MealDetailScreen(meal: Meal, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Text("Ingredients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        if (meal.ingredients.isNotEmpty()) {
            Column {
                meal.ingredients.forEach { ingredient ->
                    Text("• $ingredient")
                }
            }
        } else {
            Text("No ingredients listed.")
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Recipe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        if (meal.recipe.isNotBlank()) {
            Text(meal.recipe)
        } else {
            Text("No recipe provided.")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AvailableScreen(meals: List<Meal>, modifier: Modifier = Modifier) {
    val availableMeals = meals.filter { it.isChecked }

    Column(modifier = modifier) {
        if (availableMeals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No meals currently selected as available.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Text("Available Meals:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableMeals.size) { index ->
                    Text("• ${availableMeals[index].name}", style = MaterialTheme.typography.bodyLarge)
                    if (index < availableMeals.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun IngredientsScreen(meals: List<Meal>, modifier: Modifier = Modifier) {
    val availableMeals = meals.filter { it.isChecked }
    val allIngredients = availableMeals
        .flatMap { it.ingredients }
        .distinct()
        .sorted()

    Column(modifier = modifier) {
        if (allIngredients.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (availableMeals.isEmpty()) {
                    Text("No meals selected as available.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text("Selected meals have no ingredients listed.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            Text("Required Ingredients:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allIngredients.size) { index ->
                    Text("• ${allIngredients[index]}", style = MaterialTheme.typography.bodyLarge)
                    if (index < allIngredients.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Meal List Light")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Meal List Dark")
@Composable
fun MealListScreenPreview() {
    DinnerPlannerTheme {
        val sampleMeals = remember {
            mutableStateListOf(
                Meal(name = "Tacos", isChecked = false, ingredients=listOf("Beef", "Cheese", "Lettuce"), recipe="Cook beef..."),
                Meal(name = "Pizza", isChecked = true, ingredients=listOf("Dough", "Sauce", "Pepperoni"), recipe="Bake..."),
                Meal(name = "Salad", isChecked = false)
            )
        }
        MealListScreen(meals = sampleMeals, onMealCheckedChange = { _, _ -> }, onMealClick = {}, onMealEditClick = {}, modifier = Modifier.padding(16.dp).fillMaxSize())
    }
}

@Preview(showBackground = true)
@Composable
fun MealItemPreview() {
    DinnerPlannerTheme {
        val meal = remember { mutableStateOf(Meal(name = "Preview Meal", isChecked = true)) }
        MealItem(
            meal = meal.value,
            onCheckedChange = { isChecked -> meal.value = meal.value.copy(isChecked = isChecked) },
            onClick = {},
            onEditClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Meal Dialog Dark")
@Preview(showBackground = true, name = "Meal Dialog Light")
@Composable
fun MealDialogPreview() {
    DinnerPlannerTheme {
        MealDialog(mealToEdit = null, onDismiss = {}, onConfirmMeal = { })
    }
}

@Preview(showBackground = true)
@Composable
fun AvailableScreenPreview() {
    DinnerPlannerTheme {
        val sampleMeals = remember { mutableStateListOf(
            Meal(name="Tacos", isChecked = true),
            Meal(name="Pizza", isChecked = false),
            Meal(name="Salad", isChecked = true)
        ) }
        AvailableScreen(meals = sampleMeals, modifier = Modifier.padding(16.dp).fillMaxSize())
    }
}

@Preview(showBackground = true)
@Composable
fun IngredientsScreenPreview() {
    DinnerPlannerTheme {
        val sampleMeals = remember { mutableStateListOf(
            Meal(name="Tacos", isChecked = true, ingredients = listOf("Beef", "Cheese", "Lettuce")), 
            Meal(name="Pizza", isChecked = false, ingredients = listOf("Dough", "Sauce", "Pepperoni")), 
            Meal(name="Salad", isChecked = true, ingredients = listOf("Lettuce", "Tomato", "Cucumber")), 
            Meal(name="Sandwich", isChecked = true, ingredients = listOf("Bread", "Cheese"))
        ) }
        IngredientsScreen(meals = sampleMeals, modifier = Modifier.padding(16.dp).fillMaxSize())
    }
}

@Preview(showBackground = true)
@Composable
fun MealDetailScreenPreview() {
    DinnerPlannerTheme {
        val meal = Meal(
            id = "detailPrev",
            name = "Preview Detailed Meal",
            ingredients = listOf("Ingredient A", "Ingredient B"),
            recipe = "Step 1: Do the thing. Step 2: Do the other thing."
        )
        MealDetailScreen(meal = meal, modifier = Modifier.padding(16.dp))
    }
}