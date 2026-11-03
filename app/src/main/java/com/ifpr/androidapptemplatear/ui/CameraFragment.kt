package com.ifpr.androidapptemplatear.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.ifpr.androidapptemplatear.R
import io.github.sceneview.SceneView
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class CameraFragment : Fragment() {

    private enum class Mode { CHECKING, AR, PREVIEW_3D }

    private var mode = Mode.CHECKING
    private var sceneContainer: FrameLayout? = null
    private var botaoEscavar: Button? = null
    private var botaoColetar: Button? = null
    private var botaoPosicionar: ExtendedFloatingActionButton? = null

    private var sceneView: SceneView? = null
    private var arSceneView: ARSceneView? = null
    private var modelNode: ModelNode? = null
    private var anchorNode: AnchorNode? = null
    private var placementHit: HitResult? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraPreview: Preview? = null
    private var somEscavacao: MediaPlayer? = null
    private var loadJob: Job? = null
    private var checkJob: Job? = null
    private var generation = 0
    private var modelGeneration = 0
    private var pedraVisivel = false
    private var carregandoPedra = false

    private val pedras = listOf("Esmeralda.glb", "Safira.glb")

    private val permissaoCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (sceneContainer != null) {
            if (concedida) verificarARCore() else iniciarModoAlternativo(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sceneContainer = view.findViewById(R.id.sceneContainer)
        botaoEscavar = view.findViewById(R.id.escavar)
        botaoColetar = view.findViewById(R.id.coletar)
        botaoPosicionar = view.findViewById(R.id.place)

        botaoEscavar?.isEnabled = false
        botaoColetar?.isEnabled = false
        botaoPosicionar?.isEnabled = false
        botaoEscavar?.setOnClickListener { escavar() }
        botaoColetar?.setOnClickListener { coletar() }
        botaoPosicionar?.setOnClickListener { posicionarPedra() }

        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            verificarARCore()
        } else {
            permissaoCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("Emulator", true) ||
                Build.MODEL.contains("google_sdk", true) ||
                Build.PRODUCT.contains("sdk", true) ||
                Build.HARDWARE.contains("goldfish", true) ||
                Build.HARDWARE.contains("ranchu", true)

    private fun verificarARCore() {
        // Não deixa o SceneView abrir uma sessão AR incompatível no emulador.
        if (isEmulator()) {
            iniciarModoAlternativo(true)
            return
        }

        val expected = generation
        var resolved = false
        checkJob?.cancel()
        checkJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(4000L)
            if (!resolved && sceneContainer != null && expected == generation) {
                resolved = true
                iniciarModoAlternativo(true)
            }
        }

        try {
            ArCoreApk.getInstance().checkAvailabilityAsync(requireContext()) { availability ->
                activity?.runOnUiThread {
                    if (!resolved && sceneContainer != null && expected == generation) {
                        resolved = true
                        checkJob?.cancel()
                        if (availability == ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                            iniciarAR()
                        } else {
                            iniciarModoAlternativo(true)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            Log.e("IMPERIO_AR", "Erro na verificação ARCore", error)
            if (!resolved && sceneContainer != null && expected == generation) {
                resolved = true
                checkJob?.cancel()
                iniciarModoAlternativo(true)
            }
        }
    }

    private fun iniciarAR() {
        val host = sceneContainer ?: return
        limparCena()
        mode = Mode.AR
        val expected = generation

        try {
            val ar = ARSceneView(requireContext())
            arSceneView = ar
            sceneView = ar

            ar.sessionConfiguration = { _, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            }

            ar.onSessionFailed = { error ->
                Log.e("IMPERIO_AR", "Falha ao iniciar ARCore", error)
                host.post {
                    if (sceneContainer === host && generation == expected && mode == Mode.AR) {
                        iniciarModoAlternativo(true)
                    }
                }
            }

            ar.onSessionUpdated = { _, _ ->
                if (mode == Mode.AR && generation == expected && anchorNode == null) {
                    placementHit = if (modelNode != null) {
                        ar.hitTestAR(
                            planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)
                        )
                    } else null
                    botaoPosicionar?.isEnabled = modelNode != null && placementHit != null
                }
            }

            host.addView(ar, fullSize())
            ar.lifecycle = viewLifecycleOwner.lifecycle
            botaoPosicionar?.text = "Posicionar"
            botaoPosicionar?.isEnabled = false
            botaoEscavar?.isEnabled = true
        } catch (error: Exception) {
            Log.e("IMPERIO_AR", "Erro ao criar ARSceneView", error)
            iniciarModoAlternativo(true)
        }
    }

    private fun iniciarModoAlternativo(comCamera: Boolean) {
        val host = sceneContainer ?: return
        limparCena()
        mode = Mode.PREVIEW_3D
        host.setBackgroundColor(Color.rgb(35, 43, 55))
        val expected = generation

        // Tenta a câmera normal. Se não estiver disponível, ainda exibe a cena 3D.
        val previewView = if (comCamera) {
            PreviewView(requireContext()).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                host.addView(this, fullSize())
            }
        } else null

        try {
            val scene = SceneView(
                context = requireContext(),
                isOpaque = previewView == null
            )
            sceneView = scene
            scene.cameraNode.position = Position(0f, 0f, 3f)
            scene.cameraNode.lookAt(Position(0f, 0f, 0f))
            host.addView(scene, fullSize())
            scene.lifecycle = viewLifecycleOwner.lifecycle

            botaoPosicionar?.text = "Modo 3D"
            botaoPosicionar?.isEnabled = false
            botaoEscavar?.isEnabled = true

            if (previewView != null) abrirCameraNormal(previewView, expected)
            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    "ARCore indisponível. Escavação em modo 3D.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (error: Exception) {
            Log.e("IMPERIO_AR", "Erro na visualização 3D", error)
            botaoEscavar?.isEnabled = false
            if (isAdded) Toast.makeText(
                requireContext(), "Não foi possível iniciar a cena 3D.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun abrirCameraNormal(previewView: PreviewView, expected: Int) {
        val ctx = context ?: return
        val owner = viewLifecycleOwner
        try {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                if (sceneContainer == null || mode != Mode.PREVIEW_3D ||
                    generation != expected) return@addListener

                try {
                    val provider = future.get()
                    val selector = when {
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                            CameraSelector.DEFAULT_BACK_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> return@addListener
                    }
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    provider.bindToLifecycle(owner, selector, preview)
                    cameraProvider = provider
                    cameraPreview = preview
                } catch (error: Exception) {
                    // Sem câmera disponível no emulador: a cena 3D continua ativa.
                    Log.w("IMPERIO_AR", "CameraX indisponível", error)
                }
            }, ContextCompat.getMainExecutor(ctx))
        } catch (error: Exception) {
            Log.w("IMPERIO_AR", "Falha ao abrir CameraX", error)
        }
    }

    private fun escavar() {
        if (sceneView == null || mode == Mode.CHECKING) return
        if (pedraVisivel || carregandoPedra) {
            Toast.makeText(requireContext(), "Colete a pedra atual primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        reproduzirSom()
        carregarPedra(pedras.random())
    }

    private fun reproduzirSom() {
        somEscavacao?.release()
        somEscavacao = null
        val player = MediaPlayer.create(requireContext(), R.raw.som1) ?: return
        somEscavacao = player
        player.setOnCompletionListener {
            if (somEscavacao === it) somEscavacao = null
            it.release()
        }
        player.start()
    }

    private fun carregarPedra(nomeArquivo: String) {
        val scene = sceneView ?: return
        val asset = modelLocation(nomeArquivo)
        if (asset == null) {
            Toast.makeText(requireContext(), "GLB não encontrado: $nomeArquivo", Toast.LENGTH_LONG).show()
            return
        }

        loadJob?.cancel()
        removerPedra()
        carregandoPedra = true
        botaoEscavar?.isEnabled = false
        botaoColetar?.isEnabled = false
        botaoPosicionar?.isEnabled = false
        val expected = generation
        val expectedModel = ++modelGeneration

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val instance = scene.modelLoader.loadModelInstance(asset)
                if (generation != expected || modelGeneration != expectedModel ||
                    sceneView !== scene) return@launch

                if (instance == null) {
                    carregandoPedra = false
                    botaoEscavar?.isEnabled = true
                    Toast.makeText(requireContext(), "Não foi possível abrir $nomeArquivo", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val isAR = mode == Mode.AR
                val node = ModelNode(
                    modelInstance = instance,
                    scaleToUnits = if (isAR) 0.25f else 1.3f,
                    centerOrigin = Position(0f, 0f, 0f)
                )
                node.isEditable = true
                node.isScaleEditable = false
                modelNode = node
                pedraVisivel = true
                carregandoPedra = false

                if (isAR) {
                    botaoPosicionar?.text = "Procurando superfície..."
                    botaoPosicionar?.isEnabled = placementHit != null
                    Toast.makeText(requireContext(), "Aponte para o chão e posicione a pedra.", Toast.LENGTH_LONG).show()
                } else {
                    scene.addChildNode(node)
                    botaoColetar?.isEnabled = true
                    Toast.makeText(requireContext(), "Pedra encontrada! Toque em Coletar.", Toast.LENGTH_SHORT).show()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("IMPERIO_AR", "Erro ao carregar $nomeArquivo", error)
                if (sceneView === scene && generation == expected) {
                    carregandoPedra = false
                    botaoEscavar?.isEnabled = true
                    Toast.makeText(requireContext(), "Erro ao carregar a pedra.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun posicionarPedra() {
        if (mode != Mode.AR) return
        val ar = arSceneView ?: return
        val node = modelNode ?: return
        val hit = placementHit ?: return
        try {
            val anchor = AnchorNode(engine = ar.engine, anchor = hit.createAnchor())
            anchor.addChildNode(node)
            ar.addChildNode(anchor)
            anchorNode = anchor
            placementHit = null
            botaoPosicionar?.text = "Posicionado"
            botaoPosicionar?.isEnabled = false
            botaoColetar?.isEnabled = true
            Toast.makeText(requireContext(), "Pedra posicionada!", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Log.e("IMPERIO_AR", "Erro ao posicionar", error)
        }
    }

    private fun coletar() {
        if (!pedraVisivel || carregandoPedra) return
        removerPedra()
        botaoColetar?.isEnabled = false
        botaoPosicionar?.isEnabled = false
        botaoPosicionar?.text = if (mode == Mode.AR) "Posicionar" else "Modo 3D"
        botaoEscavar?.isEnabled = true
        Toast.makeText(requireContext(), "Pedra coletada!", Toast.LENGTH_SHORT).show()
    }

    private fun modelLocation(nomeArquivo: String): String? {
        val ctx = context ?: return null
        try {
            ctx.assets.open(nomeArquivo).use { }
            return nomeArquivo
        } catch (_: Exception) { }
        val file = File(ctx.filesDir, nomeArquivo)
        return if (file.isFile) Uri.fromFile(file).toString() else null
    }

    private fun removerPedra() {
        modelGeneration++
        modelNode?.let { node ->
            runCatching {
                anchorNode?.removeChildNode(node)
                sceneView?.removeChildNode(node)
                node.destroy()
            }
        }
        modelNode = null
        anchorNode?.let { anchor ->
            runCatching {
                sceneView?.removeChildNode(anchor)
                anchor.anchor.detach()
                anchor.destroy()
            }
        }
        anchorNode = null
        placementHit = null
        pedraVisivel = false
        carregandoPedra = false
    }

    private fun limparCena() {
        generation++
        checkJob?.cancel()
        loadJob?.cancel()
        loadJob = null
        cameraPreview?.let { preview ->
            runCatching { cameraProvider?.unbind(preview) }
        }
        cameraPreview = null
        cameraProvider = null
        removerPedra()
        sceneView?.let { scene ->
            runCatching {
                scene.lifecycle = null
                scene.destroy()
            }
        }
        sceneView = null
        arSceneView = null
        sceneContainer?.removeAllViews()
    }

    private fun fullSize() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    override fun onDestroyView() {
        limparCena()
        somEscavacao?.release()
        somEscavacao = null
        sceneContainer = null
        botaoEscavar = null
        botaoColetar = null
        botaoPosicionar = null
        mode = Mode.CHECKING
        super.onDestroyView()
    }
}
