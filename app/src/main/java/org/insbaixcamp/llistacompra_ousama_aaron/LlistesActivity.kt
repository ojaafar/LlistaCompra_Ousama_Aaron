package org.insbaixcamp.llistacompra_ousama_aaron

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputFilter
import androidx.appcompat.app.AlertDialog
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
import android.content.Intent

class LlistesActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var etNomLlista: EditText
    private lateinit var btnCrearLlista: Button
    private lateinit var tvEstatLlistes: TextView
    private lateinit var adaptador: ArrayAdapter<String>

    private var escoltaLlistes: ListenerRegistration? = null
    private var creant = false
    private var compartint = false
    private var propietaris: Map<String, String> = emptyMap()

    private var idsLlistes: List<String> = emptyList()

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
        findViewById<ListView>(R.id.lvLlistes)
            .setOnItemClickListener { _, _, posicio, _ ->
                val id = idsLlistes.getOrNull(posicio)
                    ?: return@setOnItemClickListener

                val nom = adaptador.getItem(posicio).orEmpty()

                val pantalla = Intent(this, CategoriesActivity::class.java).apply {
                    putExtra("llistaId", id)
                    putExtra("llistaNom", nom)
                }

                startActivity(pantalla)
            }

        findViewById<Button>(R.id.btnMeuCodi).setOnClickListener { mostrarCodi() }

        findViewById<ListView>(R.id.lvLlistes).setOnItemLongClickListener { _, _, posicio, _ ->
            val id = idsLlistes.getOrNull(posicio)
            if (id != null && !compartint) {
                if (propietaris[id] == auth.currentUser?.uid) compartir(id, adaptador.getItem(posicio).orEmpty())
                else Toast.makeText(this, R.string.compartir_nomes_propietari, Toast.LENGTH_LONG).show()
            }
            true
        }

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
                // Conservem junts l'identificador i el nom de cada llista.
                val llistesOrdenades = documents.documents.mapNotNull { document ->
                    val nom = document.getString("nom") ?: return@mapNotNull null
                    document.id to nom
                }.sortedBy { it.second.lowercase() }

                propietaris = documents.documents.associate { it.id to it.getString("propietariId").orEmpty() }
                idsLlistes = llistesOrdenades.map { it.first }
                val noms = llistesOrdenades.map { it.second }

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

    // El codi identifica el destinatari; no és una contrasenya.
    private fun mostrarCodi() {
        val uid = auth.currentUser?.uid ?: return
        val boto = findViewById<Button>(R.id.btnMeuCodi)
        boto.isEnabled = false
        // Registre mínim: no publiquem correus ni un directori d'usuaris.
        db.collection("usuaris").document(uid).set(mapOf("actiu" to true))
            .addOnCompleteListener { resultat ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                boto.isEnabled = true
                if (auth.currentUser?.uid != uid) return@addOnCompleteListener
                if (!resultat.isSuccessful) {
                    Log.e("Compartir", "Error preparant el codi", resultat.exception)
                    Toast.makeText(this, R.string.compartir_error_codi, Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }
                val text = TextView(this).apply {
                    this.text = getString(R.string.compartir_explicacio_codi, uid)
                    setTextIsSelectable(true)
                    val marge = (24 * resources.displayMetrics.density).toInt()
                    setPadding(marge, marge, marge, marge)
                }
                AlertDialog.Builder(this).setTitle(R.string.compartir_meu_codi)
                    .setView(text)
                    .setPositiveButton(R.string.compartir_copiar) { _, _ ->
                        if (auth.currentUser?.uid == uid) {
                            val portaRetalls = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            portaRetalls.setPrimaryClip(ClipData.newPlainText(getString(R.string.compartir_meu_codi), uid))
                        }
                    }.setNegativeButton(R.string.compartir_tancar, null).show()
            }
    }

    private fun compartir(llistaId: String, nom: String) {
        val uid = auth.currentUser?.uid ?: return
        val camp = EditText(this).apply {
            hint = getString(R.string.compartir_codi_destinatari)
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(128))
        }
        val dialeg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.compartir_titol, nom))
            .setMessage(R.string.compartir_permisos)
            .setView(camp)
            .setPositiveButton(R.string.compartir_afegir, null)
            .setNegativeButton(R.string.compartir_cancel, null).create()
        dialeg.setOnShowListener {
            dialeg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (compartint) return@setOnClickListener
                val altre = camp.text.toString().trim()
                if (!altre.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                    camp.error = getString(R.string.compartir_codi_invalid)
                    return@setOnClickListener
                }
                if (auth.currentUser?.uid != uid) { dialeg.dismiss(); return@setOnClickListener }
                compartint = true
                dialeg.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                camp.isEnabled = false
                val ref = db.collection("llistes").document(llistaId)
                // La transacció conserva els membres afegits per altres operacions.
                db.runTransaction { t ->
                    val doc = t.get(ref)
                    check(doc.exists() && doc.getString("propietariId") == uid) { "NOMES_PROPIETARI" }
                    val membres = (doc.get("membres") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    check(altre !in membres) { "JA_ES_MEMBRE" }
                    t.update(ref, "membres", membres + altre)
                    null
                }.addOnCompleteListener { resultat ->
                    if (isFinishing || isDestroyed) return@addOnCompleteListener
                    compartint = false
                    if (auth.currentUser?.uid != uid) { dialeg.dismiss(); return@addOnCompleteListener }
                    if (resultat.isSuccessful) {
                        dialeg.dismiss()
                        Toast.makeText(this, R.string.compartir_fet, Toast.LENGTH_LONG).show()
                    } else {
                        Log.e("Compartir", "Error afegint membre", resultat.exception)
                        val causes = generateSequence(resultat.exception as Throwable?) { it.cause }
                            .map { it.message.orEmpty() }.toList()
                        val missatge = when {
                            causes.any { it.contains("JA_ES_MEMBRE") } -> R.string.compartir_ja_membre
                            causes.any { it.contains("NOMES_PROPIETARI") } -> R.string.compartir_nomes_propietari
                            else -> R.string.compartir_error
                        }
                        camp.isEnabled = true
                        dialeg.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        camp.error = getString(missatge)
                        Toast.makeText(this, missatge, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialeg.show()
    }
}
