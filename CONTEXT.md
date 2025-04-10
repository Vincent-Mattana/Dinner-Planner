# Dinner Planner App - Development Context & Decisions

This file tracks the key decisions and features implemented during the development of the Dinner Planner Android app.

## Core Purpose

*   Store meals (name, ingredients, recipe).
*   Help with grocery shopping decisions.
*   Make it easier to choose meals based on available ingredients (future goal).

## Technology Stack

*   **Platform:** Android
*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose
*   **Build System:** Gradle
*   **Backend:** Firebase Firestore (for persistence and sharing)
*   **IDE:** Primarily Cursor, with Android Studio used for project setup, running/debugging, and SDK management.

## Feature Implementation Log

1.  **Basic Structure:**
    *   Created standard Android project using Android Studio (Empty Activity template).
    *   Using Jetpack Compose for UI (no XML layouts).
2.  **Initial Meal List (v1):**
    *   Single screen (`MainActivity`).
    *   Input field (`OutlinedTextField`) and "Add" button (`Button`) to add meals.
    *   Display meals in a `LazyColumn`.
    *   Used `mutableStateListOf` for the meal list state.
    *   Added checkboxes (`Checkbox`) next to each meal (basic state handling).
    *   Defined `Meal` data class (initially just `name` and `isChecked`).
3.  **UI Refactor (Tabs & Add Dialog):**
    *   Introduced `Scaffold` with `TopAppBar`.
    *   Added a "+" `IconButton` in the `TopAppBar`.
    *   Removed direct add functionality from the main screen.
    *   Implemented an `AlertDialog` (`AddMealDialog`) triggered by the "+" button for adding new meals.
    *   Added `TabRow` navigation below the `TopAppBar` with tabs: "Available", "Meals", "Ingredients".
    *   Hoisted `meals` state to `MainActivity` level.
    *   Implemented state logic for checkboxes (`onMealCheckedChange` lambda passing index).
    *   Ensured app respects system Light/Dark mode via `DinnerPlannerTheme`.
4.  **Meal Details:**
    *   Enhanced `Meal` data class to include `ingredients: List<String>` and `recipe: String`.
    *   Modified `AddMealDialog` to include `OutlinedTextField`s for ingredients (comma-separated input) and recipe.
    *   Added ability to click on a meal in the "Meals" list (`MealItem` made clickable).
    *   Created `MealDetailScreen` composable to display the selected meal's name, ingredients (bulleted list), and recipe.
    *   Implemented conditional navigation within the "Meals" tab: show `MealListScreen` or `MealDetailScreen` based on `selectedMeal` state.
    *   Updated `TopAppBar` to be context-aware: shows back arrow and meal title when viewing details, shows app title and "+" button otherwise.
    *   Hid `TabRow` when viewing meal details within the "Meals" tab.

## Completed Since Last Interaction

5.  **Meal Editing:**
    *   Added an "Edit" `IconButton` (pencil icon) to each row in the `MealListScreen`.
    *   Clicking the edit icon on a meal row opens the `MealDialog` for that meal.
    *   Renamed `AddMealDialog` to `MealDialog`.
    *   Modified `MealDialog` to accept an optional `Meal` for editing.
    *   If editing, the dialog pre-populates with the existing meal's data.
    *   Dialog title and confirm button text change dynamically ("Add"/"Add New Meal" vs. "Update"/"Edit Meal").
    *   Updated `MainActivity` state management to handle opening the dialog for either adding or editing.
    *   Implemented logic to either add a new meal or update an existing meal in the `meals` list based on the dialog's mode.
    *   Ensured the `MealDetailScreen` updates if the meal being viewed is edited.
6.  **Available Meals Tab:**
    *   Modified `AvailableScreen` to accept the full `meals` list.
    *   The screen now filters the list to show only meals where `isChecked` is true.
    *   Displays the names of the checked meals or a placeholder message if none are checked.
7.  **Ingredients Tab:**
    *   Modified `IngredientsScreen` to accept the full `meals` list.
    *   The screen filters for checked meals (`isChecked == true`).
    *   It then compiles a unique, alphabetically sorted list of all ingredients required for those selected meals.
    *   Displays the ingredient list or a placeholder message if none are needed/selected.
8.  **Firebase Firestore Integration:**
    *   Added Firebase Firestore SDK to the project.
    *   Changed `applicationId` and refactored project structure/namespace to `com.example.dinnerplanner`.
    *   Refactored `MainActivity` to read/write meal data to/from Firestore.
    *   Implemented Firestore listener using `addSnapshotListener` within `DisposableEffect` (initially tried Flow/`collectAsState`, but listener updates were unreliable post-write).
    *   Added `MetadataChanges.INCLUDE` to the listener to ensure firing after local writes.
    *   Modified `Meal` data class for Firestore compatibility (`@DocumentId`, default values, `@PropertyName`).
    *   Updated add, edit logic to perform Firestore operations (`add`, `set`).
    *   Updated check/uncheck logic (`onMealCheckedChange`) to use `set(..., SetOptions.merge())`.
    *   **Workaround:** Implemented a manual `.get()` and local state update within the `onMealCheckedChange` success callback to immediately reflect checkbox changes in the UI due to observed listener latency.
    *   Meal data now persists across app launches and syncs between users/devices connected to the same Firestore database.
9.  **Checkbox Persistence Fix:**
    *   Diagnosed issue where only one meal could be checked at a time and state wasn't persisting across restarts.
    *   Removed `MetadataChanges.INCLUDE` from the main Firestore listener to prevent it from immediately overwriting local state changes triggered by the checkbox toggle workaround.
    *   Changed the Firestore update method in `onMealCheckedChange` from `set(..., SetOptions.merge())` to `update("checked", isChecked)`.
    *   Resolved final persistence issue by renaming the Firestore field from `isChecked` to `checked` (and updating `@PropertyName` in `Meal` class and relevant Firestore calls) to avoid potential naming conflicts during serialization. Required manual data migration in Firestore.
    *   Maintained the `.get()` workaround in the `onMealCheckedChange` success listener for immediate UI feedback on the toggled checkbox.
10. **Swipeable Tabs:**
    *   Replaced the static `TabRow` index management (`selectedTabIndex`) with `HorizontalPager` and `rememberPagerState`.
    *   Used `rememberCoroutineScope` to handle tab click animations (`animateScrollToPage`).
    *   Wrapped the main content area (previously controlled by a `when` block on `selectedTabIndex`) in a `HorizontalPager`.
    *   Connected `TabRow` `onClick` events to the pager state and updated `TabRow` selection based on `pagerState.currentPage`.
    *   Ensured conditional UI logic (like `TopAppBar` content and `TabRow` visibility) correctly uses `pagerState.currentPage` and `selectedMeal` state.

## Known Issues / Next Steps

*   Ingredients are added via simple comma-separated text, no structured management yet.
*   No functionality yet to determine available meals based on ingredients.
*   Firestore security rules are set to Test Mode (insecure, needs proper rules before release).
*   No user authentication yet (all users share the same data pool).
*   The manual `.get()` workaround in `onMealCheckedChange` is still in place to provide immediate UI feedback for checkbox toggles due to potential Firestore listener latency, although the underlying persistence issues are resolved. 