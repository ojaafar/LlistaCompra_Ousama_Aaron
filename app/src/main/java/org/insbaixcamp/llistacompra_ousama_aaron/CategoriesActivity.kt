package org.insbaixcamp.llistacompra_ousama_aaron

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

class CategoriesActivity : AppCompatActivity() {

    // Guardem l'identificador per editar o eliminar el document correcte.
    private data class Categoria(
        val id: String,
        val nom: String
    ) {
        override fun toString(): String = nom
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var categoriesRef: CollectionReference
    private lateinit var adaptador: ArrayAdapter<Categoria>
    private lateinit var btnAfegir: Button
    private lateinit var lvCategories: ListView
    private lateinit var tvEstat: TextView

    private var escolta: ListenerRegistration? = null
    private var desant = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_categories)

        // La pantalla anterior ens envia la llista seleccionada.
        val llistaId = intent.getStringExtra("llistaId")
        val llistaNom = intent.getStringExtra("llistaNom").orEmpty()

        if (llistaId.isNullOrBlank() || auth.currentUser == null) {
            finish()
            return
        }

        categoriesRef = db.collection("llistes")
            .document(llistaId)
            .collection("categories")

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.arrelCategories)
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

        findViewById<TextView>(R.id.tvTitolCategories).text =
            getString(R.string.categories_titol, llistaNom)

        btnAfegir = findViewById(R.id.btnAfegirCategoria)
        lvCategories = findViewById(R.id.lvCategories)
        tvEstat = findViewById(R.id.tvEstatCategories)

        adaptador = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf<Categoria>()
        )

        lvCategories.adapter = adaptador

        btnAfegir.setOnClickListener {
            if (!desant) mostrarEditor(null)
        }

        lvCategories.setOnItemClickListener { _, _, posicio, _ ->
            if (!desant) {
                adaptador.getItem(posicio)?.let { categoria ->
                    mostrarOpcions(categoria)
                }
            }
        }

        findViewById<Button>(R.id.btnTornarLlistes).setOnClickListener {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()

        if (isFinishing || !::categoriesRef.isInitialized) return

        val uid = auth.currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        tvEstat.isVisible = true
        tvEstat.setText(R.string.categories_carregant)

        escolta?.remove()

        escolta = categoriesRef
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
                    Log.e("Categories", "Error en llegir categories", error)
                    adaptador.clear()
                    tvEstat.isVisible = true
                    tvEstat.setText(R.string.categories_error_llegir)
                    return@addSnapshotListener
                }

                val documents = resultat ?: return@addSnapshotListener

                val categories = documents.documents.mapNotNull { document ->
                    val nom = document.getString("nom")
                        ?: return@mapNotNull null

                    Categoria(document.id, nom)
                }.sortedBy { it.nom.lowercase() }

                adaptador.clear()
                adaptador.addAll(categories)
                adaptador.notifyDataSetChanged()

                val missatge = when {
                    documents.metadata.hasPendingWrites() ->
                        R.string.categories_pendents

                    documents.metadata.isFromCache ->
                        R.string.categories_cache

                    categories.isEmpty() ->
                        R.string.categories_buides

                    else -> 0
                }

                tvEstat.isVisible = missatge != 0
                if (missatge != 0) tvEstat.setText(missatge)
            }
    }

    override fun onStop() {
        escolta?.remove()
        escolta = null
        super.onStop()
    }

    private fun mostrarOpcions(categoria: Categoria) {
        val opcions = arrayOf(
            getString(R.string.categories_editar),
            getString(R.string.categories_eliminar)
        )

        AlertDialog.Builder(this)
            .setTitle(categoria.nom)
            .setItems(opcions) { _, posicio ->
                when (posicio) {
                    0 -> mostrarEditor(categoria)
                    1 -> confirmarEliminacio(categoria)
                }
            }
            .setNegativeButton(R.string.categories_cancel, null)
            .show()
    }

    private fun mostrarEditor(categoria: Categoria?) {
        val campNom = EditText(this).apply {
            hint = getString(R.string.categories_nom)
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(80))
            setText(categoria?.nom.orEmpty())
            setSelection(text.length)
        }

        val dialeg = AlertDialog.Builder(this)
            .setTitle(
                if (categoria == null) R.string.categories_nova
                else R.string.categories_editar
            )
            .setView(campNom)
            .setPositiveButton(R.string.categories_desar, null)
            .setNegativeButton(R.string.categories_cancel, null)
            .create()

        dialeg.setOnShowListener {
            // Personalitzem el botó perquè el diàleg no es tanqui
            // quan el nom està buit.
            dialeg.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    if (desant) return@setOnClickListener

                    val nom = campNom.text.toString().trim()

                    if (nom.isEmpty() || nom.length > 80) {
                        campNom.error =
                            getString(R.string.categories_error_nom)
                        return@setOnClickListener
                    }

                    if (auth.currentUser == null) {
                        dialeg.dismiss()
                        finish()
                        return@setOnClickListener
                    }

                    dialeg.dismiss()

                    val tasca = if (categoria == null) {
                        val dades = hashMapOf<String, Any>(
                            "nom" to nom,
                            "creadaEl" to FieldValue.serverTimestamp()
                        )

                        categoriesRef.document().set(dades)
                    } else {
                        // update conserva la data original de creació.
                        categoriesRef.document(categoria.id)
                            .update("nom", nom)
                    }

                    executarCanvi(tasca)
                }
        }

        dialeg.show()
    }

    private fun confirmarEliminacio(categoria: Categoria) {
        AlertDialog.Builder(this)
            .setTitle(R.string.categories_eliminar)
            .setMessage(
                getString(
                    R.string.categories_confirmar_eliminar,
                    categoria.nom
                )
            )
            .setNegativeButton(R.string.categories_cancel, null)
            .setPositiveButton(R.string.categories_eliminar) { _, _ ->
                if (!desant && auth.currentUser != null) {
                    executarCanvi(
                        categoriesRef.document(categoria.id).delete()
                    )
                }
            }
            .show()
    }

    private fun executarCanvi(tasca: Task<Void>) {
        desant = true
        btnAfegir.isEnabled = false
        lvCategories.isEnabled = false

        tasca.addOnCompleteListener { resultat ->
            if (isFinishing || isDestroyed) {
                return@addOnCompleteListener
            }

            desant = false
            btnAfegir.isEnabled = true
            lvCategories.isEnabled = true

            if (resultat.isSuccessful) {
                Toast.makeText(
                    this,
                    R.string.categories_canvi_desat,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Log.e(
                    "Categories",
                    "Error en modificar una categoria",
                    resultat.exception
                )

                Toast.makeText(
                    this,
                    R.string.categories_error_desar,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}