package org.insbaixcamp.llistacompra_ousama_aaron

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class ProductesActivity : AppCompatActivity() {
    private data class Producte(val id: String, val nom: String, val etiquetes: String, val comprat: Boolean) {
        override fun toString(): String = if (etiquetes.isBlank()) nom else "$nom\n$etiquetes"
    }
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var categoriaRef: DocumentReference
    private lateinit var productesRef: CollectionReference
    private lateinit var adaptador: ArrayAdapter<Producte>
    private lateinit var llista: ListView
    private lateinit var afegir: Button
    private lateinit var estat: TextView
    private var escolta: ListenerRegistration? = null
    private var escoltaCategoria: ListenerRegistration? = null
    private var desant = false
    private var activa = false
    private var uidPantalla = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_productes)
        val llistaId = intent.getStringExtra("llistaId")
        val categoriaId = intent.getStringExtra("categoriaId")
        val uid = auth.currentUser?.uid
        if (llistaId.isNullOrBlank() || categoriaId.isNullOrBlank() || uid == null) {
            finish()
            return
        }
        uidPantalla = uid
        categoriaRef = db.collection("llistes").document(llistaId).collection("categories").document(categoriaId)
        productesRef = categoriaRef.collection("productes")
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.arrelProductes)) { vista, insets ->
            val espai = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            vista.setPadding(espai.left, espai.top, espai.right, espai.bottom)
            insets
        }
        findViewById<TextView>(R.id.tvTitolProductes).text = getString(
            R.string.productes_titol, intent.getStringExtra("categoriaNom").orEmpty())
        llista = findViewById(R.id.lvProductes)
        afegir = findViewById(R.id.btnAfegirProducte)
        estat = findViewById(R.id.tvEstatProductes)
        adaptador = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, mutableListOf<Producte>())
        llista.adapter = adaptador
        afegir.setOnClickListener { if (!desant) editor(null) }
        llista.setOnItemClickListener { _, _, posicio, _ ->
            val p = adaptador.getItem(posicio) ?: return@setOnItemClickListener
            // La marca visual reflecteix les dades de Firestore, no un canvi encara no confirmat.
            pintarMarques()
            if (!desant) executar { productesRef.document(p.id).update("comprat", !p.comprat) }
        }
        llista.setOnItemLongClickListener { _, _, posicio, _ ->
            if (!desant) adaptador.getItem(posicio)?.let { opcions(it) }
            true
        }
        findViewById<Button>(R.id.btnTornarCategories).setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        if (isFinishing || !::productesRef.isInitialized) return
        if (auth.currentUser?.uid != uidPantalla) { finish(); return }
        activa = true
        escoltaCategoria = categoriaRef.addSnapshotListener { doc, error ->
            if (!activa || isFinishing || isDestroyed) return@addSnapshotListener
            if (auth.currentUser?.uid != uidPantalla) { finish(); return@addSnapshotListener }
            if (error != null || (doc != null && !doc.exists() && !doc.metadata.isFromCache)) {
                Toast.makeText(this, R.string.productes_categoria_inexistent, Toast.LENGTH_LONG).show()
                finish()
            } else if (doc != null && doc.exists()) {
                findViewById<TextView>(R.id.tvTitolProductes).text =
                    getString(R.string.productes_titol, doc.getString("nom").orEmpty())
            }
        }
        escolta = productesRef.addSnapshotListener(MetadataChanges.INCLUDE) { resultat, error ->
            if (!activa || isFinishing || isDestroyed) return@addSnapshotListener
            if (auth.currentUser?.uid != uidPantalla) { adaptador.clear(); finish(); return@addSnapshotListener }
            if (error != null) {
                Log.e("Productes", "Error de lectura", error)
                adaptador.clear()
                llista.clearChoices()
                estat.isVisible = true
                estat.setText(R.string.productes_error_lectura)
                return@addSnapshotListener
            }
            val docs = resultat ?: return@addSnapshotListener
            val productes = docs.documents.mapNotNull { d ->
                val nom = d.getString("nom") ?: return@mapNotNull null
                Producte(d.id, nom, d.getString("etiquetes").orEmpty(), d.getBoolean("comprat") ?: false)
            }.sortedBy { it.nom.lowercase() }
            adaptador.clear()
            adaptador.addAll(productes)
            adaptador.notifyDataSetChanged()
            pintarMarques()
            val missatge = when {
                docs.metadata.hasPendingWrites() -> R.string.productes_pendents
                docs.metadata.isFromCache -> R.string.productes_cache
                productes.isEmpty() -> R.string.productes_buits
                else -> 0
            }
            estat.isVisible = missatge != 0
            if (missatge != 0) estat.setText(missatge)
        }
    }

    override fun onStop() {
        activa = false
        escolta?.remove()
        escoltaCategoria?.remove()
        escolta = null
        escoltaCategoria = null
        super.onStop()
    }

    private fun pintarMarques() {
        llista.clearChoices()
        for (i in 0 until adaptador.count) llista.setItemChecked(i, adaptador.getItem(i)?.comprat == true)
    }

    private fun opcions(p: Producte) {
        AlertDialog.Builder(this).setTitle(p.nom)
            .setItems(arrayOf(getString(R.string.productes_editar), getString(R.string.productes_eliminar))) { _, i ->
                if (i == 0) editor(p) else confirmarEliminacio(p)
            }.setNegativeButton(R.string.productes_cancel, null).show()
    }

    private fun editor(p: Producte?) {
        val formulari = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val marge = (24 * resources.displayMetrics.density).toInt()
            setPadding(marge, 0, marge, 0)
        }
        val nom = EditText(this).apply {
            hint = getString(R.string.productes_nom)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(80))
            setText(p?.nom.orEmpty())
        }
        val etiquetes = EditText(this).apply {
            hint = getString(R.string.productes_etiquetes)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(200))
            setText(p?.etiquetes.orEmpty())
        }
        formulari.addView(nom)
        formulari.addView(etiquetes)
        val dialeg = AlertDialog.Builder(this)
            .setTitle(if (p == null) R.string.productes_afegir else R.string.productes_editar)
            .setView(formulari).setPositiveButton(R.string.productes_desar, null)
            .setNegativeButton(R.string.productes_cancel, null).create()
        dialeg.setOnShowListener {
            dialeg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (desant) return@setOnClickListener
                val nomNet = nom.text.toString().trim()
                val etiquetesNetes = etiquetes.text.toString().split(',')
                    .map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
                if (nomNet.isEmpty() || nomNet.length > 80) {
                    nom.error = getString(R.string.productes_error_nom)
                    return@setOnClickListener
                }
                if (etiquetesNetes.length > 200) {
                    etiquetes.error = getString(R.string.productes_error_etiquetes)
                    return@setOnClickListener
                }
                dialeg.dismiss()
                if (p == null) crear(nomNet, etiquetesNetes)
                else executar { productesRef.document(p.id).update(mapOf("nom" to nomNet, "etiquetes" to etiquetesNetes)) }
            }
        }
        dialeg.show()
    }

    private fun crear(nom: String, etiquetes: String) {
        val nou = productesRef.document() // El mateix ID s'utilitza en tots els reintents de la transacció.
        executar {
            db.runTransaction { t ->
                val categoria = t.get(categoriaRef)
                check(categoria.exists()) { "CATEGORIA_INEXISTENT" }
                val quantitat = categoria.getLong("numProductes") ?: 0L
                t.set(nou, mapOf("nom" to nom, "etiquetes" to etiquetes, "comprat" to false,
                    "creatEl" to FieldValue.serverTimestamp()))
                t.update(categoriaRef, mapOf("numProductes" to quantitat + 1, "canviProducte" to nou.id))
                null
            }
        }
    }

    private fun confirmarEliminacio(p: Producte) {
        AlertDialog.Builder(this).setTitle(R.string.productes_eliminar)
            .setMessage(getString(R.string.productes_confirmar, p.nom))
            .setNegativeButton(R.string.productes_cancel, null)
            .setPositiveButton(R.string.productes_eliminar) { _, _ ->
                executar {
                    db.runTransaction { t ->
                        val ref = productesRef.document(p.id)
                        // Totes les lectures es fan abans de les escriptures.
                        val categoria = t.get(categoriaRef)
                        val producte = t.get(ref)
                        check(categoria.exists()) { "CATEGORIA_INEXISTENT" }
                        check(producte.exists()) { "PRODUCTE_INEXISTENT" }
                        val quantitat = categoria.getLong("numProductes") ?: 0L
                        check(quantitat > 0) { "COMPTADOR_INVALID" }
                        t.delete(ref)
                        t.update(categoriaRef, mapOf("numProductes" to quantitat - 1, "canviProducte" to p.id))
                        null
                    }
                }
            }.show()
    }

    private fun executar(operacio: () -> Task<*>) {
        if (desant) return
        if (auth.currentUser?.uid != uidPantalla) { finish(); return }
        desant = true
        afegir.isEnabled = false
        llista.isEnabled = false
        estat.isVisible = true
        estat.setText(R.string.productes_desant)
        operacio().addOnCompleteListener { resultat ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            desant = false
            afegir.isEnabled = true
            llista.isEnabled = true
            if (auth.currentUser?.uid != uidPantalla) { finish(); return@addOnCompleteListener }
            val causes = generateSequence(resultat.exception as Throwable?) { it.cause }.map { it.message.orEmpty() }.toList()
            val missatge = when {
                resultat.isSuccessful -> R.string.productes_desat
                causes.any { it.contains("CATEGORIA_INEXISTENT") } -> R.string.productes_categoria_inexistent
                causes.any { it.contains("PRODUCTE_INEXISTENT") } -> R.string.productes_inexistent
                else -> R.string.productes_error
            }
            if (!resultat.isSuccessful) Log.e("Productes", "Error en desar", resultat.exception)
            estat.isVisible = true
            estat.setText(missatge)
        }
    }
}
