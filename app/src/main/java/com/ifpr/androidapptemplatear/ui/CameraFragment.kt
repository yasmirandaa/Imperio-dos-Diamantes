
package com.ifpr.androidapptemplatear.ui

import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
        "Esmeralda.glb",
        "Safira.glb"
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

        sceneView = view.findViewById(R.id.sceneView)

        botaoEscavar = view.findViewById(R.id.escavar)
        botaoColetar = view.findViewById(R.id.coletar)

        botaoEscavar.isEnabled = false
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

        // Reproduz o som da escavação
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
        pedraVisivel = false

        botaoEscavar.isEnabled = false
        botaoColetar.isEnabled = false

        try {
            val arquivoExiste = requireContext()
                .assets
                .list("")
                ?.contains(nomeArquivo) == true

            Log.d(
                "TESTE_AR",
                "Arquivo $nomeArquivo encontrado: $arquivoExiste"
            )

            if (!arquivoExiste) {
                Toast.makeText(
                    requireContext(),
                    "Arquivo não encontrado: $nomeArquivo",
                    Toast.LENGTH_LONG
                ).show()

                carregandoPedra = false
                botaoEscavar.isEnabled = arReady
                return
            }

            val novoModelo = ArModelNode(
                sceneView.engine,
                PlacementMode.INSTANT
            )

            // Guarda a referência para permitir a coleta
            modelNode = novoModelo

            sceneView.addChild(novoModelo)

            Log.d(
                "TESTE_AR",
                "Iniciando carregamento de $nomeArquivo"
            )

            novoModelo.loadModelGlbAsync(
                glbFileLocation = nomeArquivo,
                centerOrigin = Position(0f, 0f, 1f)
            ) {

                Log.d(
                    "TESTE_AR",
                    "Carregamento concluído: $nomeArquivo"
                )

                if (!isAdded) return@loadModelGlbAsync

                carregandoPedra = false
                pedraVisivel = true

                botaoEscavar.isEnabled = false
                botaoColetar.isEnabled = true

                Toast.makeText(
                    requireContext(),
                    "Pedra encontrada! Toque em Coletar.",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (e: Exception) {

            Log.e(
                "TESTE_AR",
                "Erro no carregamento de $nomeArquivo",
                e
            )

            modelNode?.let {
                sceneView.removeChild(it)
            }

            modelNode = null
            carregandoPedra = false
            pedraVisivel = false

            botaoEscavar.isEnabled = arReady
            botaoColetar.isEnabled = false

            Toast.makeText(
                requireContext(),
                "Não foi possível carregar a pedra.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun coletar() {
        if (!pedraVisivel || carregandoPedra) return

        modelNode?.let {
            sceneView.removeChild(it)
        }

        modelNode = null

        pedraVisivel = false
        carregandoPedra = false

        botaoColetar.isEnabled = false
        botaoEscavar.isEnabled = arReady

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

        modelNode?.let {
            sceneView.removeChild(it)
        }

        modelNode = null

        somEscavacao?.release()
        somEscavacao = null

        super.onDestroyView()
    }
}
