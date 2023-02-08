package de.hpi.etranslation

import com.bluelinelabs.conductor.Controller
import dagger.hilt.EntryPoints

inline fun <reified T> Controller.entryPoint() = lazy {
    EntryPoints.get(applicationContext!!, T::class.java)!!
}
