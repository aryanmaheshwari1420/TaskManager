# Task Manager — Android App

A simple task manager app built with **Kotlin**, **MVVM**, and **Room** for local storage.

---

## Features

- Add, edit, and delete tasks
- Real-time search by title
- Data persists across app restarts (Room database)

---

## Requirements

- Android Studio Hedgehog or newer
- JDK 11+
- Min SDK: 24 (Android 7.0) | Target SDK: 34

---

## Run the App

1. Open the project in Android Studio
2. Let Gradle sync finish
3. Select a device or emulator (API 24+)
4. Click **Run ▶**

To build a debug APK:
`Build → Build Bundle(s) / APK(s) → Build APK(s)`

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## How to Use

- **Add task** — tap the ➕ FAB
- **Edit task** — tap any task in the list
- **Delete task** — long press a task
- **Search** — type in the search bar at the top

---

## Tech Stack

| Layer | Tech |
|---|---|
| Language | Kotlin |
| Architecture | MVVM |
| UI | ViewBinding, RecyclerView, Material |
| Database | Room + KSP |
| Async | Coroutines + LiveData |

---

## Project Structure

```
data/local/       → Task entity, DAO, Database
data/repository/  → TaskRepository
ui/adapter/       → TaskAdapter (RecyclerView)
ui/viewmodel/     → TaskViewModel
ui/activity/      → AddEditTaskActivity
MainActivity.kt   → Task list + search
```
