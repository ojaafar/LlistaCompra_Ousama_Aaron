package org.insbaixcamp.llistacompra_ousama_aaron

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class MainActivity : AppCompatActivity() {

    // Servicio que gestiona las cuentas y la sesión.
    private lateinit var auth: FirebaseAuth

    // Referencias a los elementos definidos en el XML.
    private lateinit var formulari: View
    private lateinit var panellSessio: View
    private lateinit var etCorreu: EditText
    private lateinit var etContrasenya: EditText
    private lateinit var btnEntrar: Button
    private lateinit var btnRegistrar: Button
    private lateinit var btnSortir: Button
    private lateinit var tvError: TextView
    private lateinit var tvUsuari: TextView
    private lateinit var progressBar: ProgressBar

    // Evita enviar varias peticiones al pulsar repetidamente.
    private var carregant = false

    // Actualiza la pantalla cuando cambia la sesión de Firebase.
    private val listenerAuth = FirebaseAuth.AuthStateListener {
        actualitzarSessio()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Reserva espacio para las barras del sistema y el teclado.
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { vista, insets ->
            val espais = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )

            vista.setPadding(
                espais.left,
                espais.top,
                espais.right,
                espais.bottom
            )
            insets
        }

        auth = FirebaseAuth.getInstance()

        formulari = findViewById(R.id.formulari)
        panellSessio = findViewById(R.id.panellSessio)
        etCorreu = findViewById(R.id.etCorreu)
        etContrasenya = findViewById(R.id.etContrasenya)
        btnEntrar = findViewById(R.id.btnEntrar)
        btnRegistrar = findViewById(R.id.btnRegistrar)
        btnSortir = findViewById(R.id.btnSortir)
        tvError = findViewById(R.id.tvError)
        tvUsuari = findViewById(R.id.tvUsuari)
        progressBar = findViewById(R.id.progressBar)

        btnEntrar.setOnClickListener {
            autenticar(crearCompte = false)
        }

        btnRegistrar.setOnClickListener {
            autenticar(crearCompte = true)
        }

        btnSortir.setOnClickListener {
            auth.signOut()
            etContrasenya.text.clear()
            actualitzarSessio()
        }

        actualitzarSessio()
    }

    override fun onStart() {
        super.onStart()

        // Firebase también avisa del estado de la sesión al añadirlo.
        auth.addAuthStateListener(listenerAuth)
    }

    override fun onStop() {
        auth.removeAuthStateListener(listenerAuth)
        super.onStop()
    }

    private fun autenticar(crearCompte: Boolean) {
        if (carregant) return

        tvError.isVisible = false

        // Quitamos espacios sobrantes del correo.
        val correu = etCorreu.text.toString().trim()

        // La contraseña se conserva exactamente como se ha escrito.
        val contrasenya = etContrasenya.text.toString()

        if (!Patterns.EMAIL_ADDRESS.matcher(correu).matches()) {
            mostrarError(getString(R.string.error_correu))
            return
        }

        if (contrasenya.isEmpty()) {
            mostrarError(getString(R.string.error_contrasenya_buida))
            return
        }

        if (crearCompte && contrasenya.length < 6) {
            mostrarError(getString(R.string.error_contrasenya_curta))
            return
        }

        mostrarCarrega(true)

        // Elegimos la operación según el botón pulsado.
        val operacio = if (crearCompte) {
            auth.createUserWithEmailAndPassword(correu, contrasenya)
        } else {
            auth.signInWithEmailAndPassword(correu, contrasenya)
        }

        // Firebase responde después: la pantalla puede seguir funcionando.
        operacio.addOnCompleteListener { resultat ->
            // Evita actualizar una actividad que ya se ha destruido.
            if (isFinishing || isDestroyed) {
                return@addOnCompleteListener
            }

            mostrarCarrega(false)

            if (resultat.isSuccessful) {
                etContrasenya.text.clear()
                actualitzarSessio()
            } else {
                // El detalle técnico queda en Logcat.
                Log.w(
                    "Autenticacio",
                    "Error en registrar o iniciar sessio",
                    resultat.exception
                )

                // El usuario recibe un mensaje comprensible.
                mostrarError(missatgeError(resultat.exception))
            }
        }
    }

    private fun actualitzarSessio() {
        val usuari = auth.currentUser
        val teSessio = usuari != null

        formulari.isVisible = !teSessio
        panellSessio.isVisible = teSessio
        tvError.isVisible = false
        tvUsuari.text = usuari?.email.orEmpty()

        if (teSessio) {
            etContrasenya.text.clear()

            // Cierra el teclado al mostrar la sesión iniciada.
            WindowCompat.getInsetsController(window, window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
    }

    private fun mostrarCarrega(activa: Boolean) {
        carregant = activa
        progressBar.isVisible = activa

        // Desactiva los controles mientras se procesa la petición.
        etCorreu.isEnabled = !activa
        etContrasenya.isEnabled = !activa
        btnEntrar.isEnabled = !activa
        btnRegistrar.isEnabled = !activa
        btnSortir.isEnabled = !activa
    }

    private fun mostrarError(missatge: String) {
        tvError.text = missatge
        tvError.isVisible = true
    }

    private fun missatgeError(error: Exception?): String {
        val recurs = when (error) {
            is FirebaseNetworkException ->
                R.string.error_xarxa

            is FirebaseTooManyRequestsException ->
                R.string.error_intents

            is FirebaseAuthWeakPasswordException ->
                R.string.error_contrasenya_debil

            is FirebaseAuthUserCollisionException ->
                R.string.error_compte_existent

            is FirebaseAuthInvalidCredentialsException,
            is FirebaseAuthInvalidUserException ->
                R.string.error_credencials

            else ->
                R.string.error_general
        }

        return getString(recurs)
    }
}