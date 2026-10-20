
package com.ifpr.androidapptemplatear.ui

import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.ar.core.Config
import com.ifpr.androidapptemplatear.R
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Position

class CameraFragment : Fragment() {

    private lateinit var sceneView: ArSceneView
    private lateinit var botaoEscavar: Button
    private lateinit var botaoColetar: Button

    private var modelNode: ArModelNode? = null
    private var somEscavacao: MediaPlayer? = null
    private var arReady = false
    private var pedraVisivel = false
    private var carregandoPedra = false

    private val cameraPermissionCode = 1001

    private val pedras = listOf(
        "Diamante.glb",
        "Rubi.glb",
        "Safira.glb",
        "Esmeralda.glb"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_camera,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sceneView = view.findViewById<ArSceneView>(R.id.sceneView).apply {
            lightEstimationMode = Config.LightEstimationMode.DISABLED
        }

        botaoEscavar = view.findViewById(R.id.escavar)
        botaoColetar = view.findViewById(R.id.coletar)

        botaoColetar.isEnabled = false

        botaoEscavar.setOnClickListener {
            escavar()
        }

        botaoColetar.setOnClickListener {
            coletar()
        }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(android.Manifest.permission.CAMERA),
                cameraPermissionCode
            )
        } else {
            initAR()
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode != cameraPermissionCode) return

        if (
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            initAR()
        } else {
            Toast.makeText(
                requireContext(),
                "Permissão de câmera negada.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initAR() {
        arReady = true
        botaoEscavar.isEnabled = true
    }

    private fun escavar() {
        if (!arReady) {
            Toast.makeText(
                requireContext(),
                "Aguarde a inicialização da câmera.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (pedraVisivel || carregandoPedra) {
            Toast.makeText(
                requireContext(),
                "Colete a pedra atual antes de escavar novamente.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val pedraSorteada = pedras.random()

        somEscavacao?.release()
        somEscavacao = MediaPlayer.create(
            requireContext(),
            R.raw.som1
        )

        somEscavacao?.setOnCompletionListener { player ->
            player.release()
            if (somEscavacao === player) {
                somEscavacao = null
            }
        }

        somEscavacao?.start()

        carregarPedra(pedraSorteada)
    }

    private fun carregarPedra(nomeArquivo: String) {
        carregandoPedra = true
        botaoEscavar.isEnabled = false
        botaoColetar.isEnabled = false

        val novoModelo = ArModelNode(
            sceneView.engine,
            PlacementMode.INSTANT
        ).apply {
            isEditable = true
            isScaleEditable = false
        }

        modelNode?.let { sceneView.removeChild(it) }
        modelNode = novoModelo
        sceneView.addChild(novoModelo)

        novoModelo.loadModelGlbAsync(
            glbFileLocation = nomeArquivo,
            centerOrigin = Position(0f, 0f, 1f)
        ) {
            if (!isAdded || modelNode !== novoModelo) {
                return@loadModelGlbAsync
            }

            carregandoPedra = false
            pedraVisivel = true

            botaoEscavar.isEnabled = true
            botaoColetar.isEnabled = true

            sceneView.planeRenderer.isVisible = true

            Toast.makeText(
                requireContext(),
                "Você encontrou: ${nomeArquivo.removeSuffix(".glb")}!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun coletar() {
        if (!pedraVisivel || carregandoPedra) return

        modelNode?.let { sceneView.removeChild(it) }
        modelNode = null

        pedraVisivel = false
        botaoColetar.isEnabled = false
        botaoEscavar.isEnabled = true

        Toast.makeText(
            requireContext(),
            "Pedra coletada!",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        arReady = false
        pedraVisivel = false
        carregandoPedra = false

        modelNode?.let { sceneView.removeChild(it) }
        modelNode = null

        somEscavacao?.release()
        somEscavacao = null

        super.onDestroyView()
    }
}
