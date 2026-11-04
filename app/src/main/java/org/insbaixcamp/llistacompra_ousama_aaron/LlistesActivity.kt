package org.insbaixcamp.llistacompra_ousama_aaron

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

class LlistesActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var etNomLlista: EditText
    private lateinit var btnCrearLlista: Button
    private lateinit var tvEstatLlistes: TextView
    private lateinit var adaptador: ArrayAdapter<String>

    private var escoltaLlistes: ListenerRegistration? = null
    private var creant = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_llistes)

        // Evita que les barres del sistema o el teclat tapin el contingut.
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.arrelLlistes)
        ) { vista, insets ->
            val espai = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )

            vista.setPadding(
                espai.left,
                espai.top,
                espai.right,
                espai.bottom
            )

            insets
        }

        etNomLlista = findViewById(R.id.etNomLlista)
        btnCrearLlista = findViewById(R.id.btnCrearLlista)
        tvEstatLlistes = findViewById(R.id.tvEstatLlistes)

        // Connecta els noms de les llistes amb el ListView.
        adaptador = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf<String>()
        )

        findViewById<ListView>(R.id.lvLlistes).adapter = adaptador

        btnCrearLlista.setOnClickListener {
            crearLlista()
        }

        findViewById<Button>(R.id.btnTornar).setOnClickListener {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()

        val uid = auth.currentUser?.uid

        // Aquesta pantalla necessita un usuari identificat.
        if (uid == null) {
            finish()
            return
        }

        observarLlistes(uid)
    }

    override fun onStop() {
        // Aturem l'escolta quan la pantalla deixa de ser visible.
        escoltaLlistes?.remove()
        escoltaLlistes = null
        super.onStop()
    }

    private fun observarLlistes(uid: String) {
        tvEstatLlistes.isVisible = true
        tvEstatLlistes.setText(R.string.carregant_llistes)

        escoltaLlistes?.remove()

        escoltaLlistes = db.collection("llistes")
            // Llegim només les llistes on l'usuari és membre.
            .whereArrayContains("membres", uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { resultat, error ->

                if (isFinishing || isDestroyed) {
                    return@addSnapshotListener
                }

                if (auth.currentUser?.uid != uid) {
                    adaptador.clear()
                    finish()
                    return@addSnapshotListener
                }

                if (error != null) {
                    Log.e("Llistes", "Error en llegir les llistes", error)

                    adaptador.clear()
                    tvEstatLlistes.isVisible = true
                    tvEstatLlistes.setText(R.string.error_llegir_llistes)
                    return@addSnapshotListener
                }

                val documents = resultat ?: return@addSnapshotListener

                // Ordenem els noms alfabèticament al mòbil.
                val noms = documents.documents
                    .mapNotNull { document -> document.getString("nom") }
                    .sortedBy { nom -> nom.lowercase() }

                adaptador.clear()
                adaptador.addAll(noms)
                adaptador.notifyDataSetChanged()

                // Informem si encara hi ha dades pendents de sincronitzar.
                val missatge = when {
                    documents.metadata.hasPendingWrites() ->
                        R.string.llistes_pendents

                    documents.metadata.isFromCache ->
                        R.string.llistes_cache

                    noms.isEmpty() ->
                        R.string.llistes_buides

                    else -> 0
                }

                tvEstatLlistes.isVisible = missatge != 0

                if (missatge != 0) {
                    tvEstatLlistes.setText(missatge)
                }
            }
    }

    private fun crearLlista() {
        // Evita enviar diverses creacions mentre una està en curs.
        if (creant) return

        val uid = auth.currentUser?.uid

        if (uid == null) {
            finish()
            return
        }

        val nom = etNomLlista.text.toString().trim()

        if (nom.isEmpty() || nom.length > 80) {
            etNomLlista.error = getString(R.string.error_nom_llista)
            return
        }

        // Aquests camps coincideixen amb les regles de Firestore.
        val dades = hashMapOf<String, Any>(
            "nom" to nom,
            "propietariId" to uid,
            "membres" to listOf(uid),
            "creadaEl" to FieldValue.serverTimestamp()
        )

        mostrarCreacio(true)

        // Firestore genera automàticament l'identificador del document.
        db.collection("llistes")
            .add(dades)
            .addOnCompleteListener { resultat ->
                if (isFinishing || isDestroyed) {
                    return@addOnCompleteListener
                }

                mostrarCreacio(false)

                if (auth.currentUser?.uid != uid) {
                    finish()
                    return@addOnCompleteListener
                }

                if (resultat.isSuccessful) {
                    etNomLlista.text.clear()

                    Toast.makeText(
                        this,
                        R.string.llista_desada,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.e(
                        "Llistes",
                        "Error en crear la llista",
                        resultat.exception
                    )

                    Toast.makeText(
                        this,
                        R.string.error_crear_llista,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun mostrarCreacio(activa: Boolean) {
        creant = activa
        etNomLlista.isEnabled = !activa
        btnCrearLlista.isEnabled = !activa

        btnCrearLlista.setText(
            if (activa) R.string.desant_llista
            else R.string.crear_llista
        )
    }
}