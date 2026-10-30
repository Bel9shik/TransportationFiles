package kardash.project.transportfiles

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var picker: MethodChannel.Result? = null
    private var exportSource: String? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var permission: MethodChannel.Result? = null

    override fun configureFlutterEngine(engine: FlutterEngine) {
        super.configureFlutterEngine(engine)
        MethodChannel(engine.dartExecutor.binaryMessenger, "transportfiles/system").setMethodCallHandler { call, result ->
            when (call.method) {
                "receivedDirectory" -> result.success(File(filesDir, "received").apply { mkdirs() }.path)
                "chooseFolder" -> {
                    if (picker != null) result.error("busy", "Выбор папки уже открыт", null)
                    else { picker = result; exportSource = null; startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 610) }
                }
                "downloadsPermission" -> {
                    if (Build.VERSION.SDK_INT >= 29 || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) result.success(true)
                    else if (permission != null) result.error("busy", "Запрос разрешения уже открыт", null)
                    else { permission = result; requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 611) }
                }
                "exportAs" -> {
                    val path = call.argument<String>("path")!!
                    if (picker != null) result.error("busy", "Выбор файла уже открыт", null)
                    else { picker = result; exportSource = path; startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime(File(path))).putExtra(Intent.EXTRA_TITLE, File(path).name), 612) }
                }
                "exportFiles" -> worker.execute {
                    val created = mutableListOf<Uri>()
                    val local = mutableListOf<File>()
                    try {
                        val paths = call.argument<List<String>>("paths")!!
                        val tree = call.argument<String>("folder")
                        for (path in paths) {
                            val source = File(path)
                            val uri = if (tree != null) {
                                val root = Uri.parse(tree)
                                DocumentsContract.createDocument(contentResolver, DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root)), mime(source), source.name)
                                    ?: error("Папка недоступна")
                            } else if (Build.VERSION.SDK_INT >= 29) {
                                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                                    put(MediaStore.MediaColumns.MIME_TYPE, mime(source))
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/TransportationFiles")
                                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                                }) ?: error("Не удалось создать файл в Загрузках")
                            } else null
                            if (uri != null) {
                                created.add(uri)
                                contentResolver.openOutputStream(uri, "w")!!.use { out -> source.inputStream().use { it.copyTo(out) } }
                            } else {
                                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TransportationFiles").apply { mkdirs() }
                                var target = File(directory, source.name)
                                var index = 1
                                while (!target.createNewFile()) {
                                    if (index > 10000) error("Не удалось подобрать имя")
                                    target = File(directory, "${source.nameWithoutExtension} (${index++})${if (source.extension.isEmpty()) "" else ".${source.extension}"}")
                                }
                                local.add(target)
                                target.outputStream().use { out -> source.inputStream().use { it.copyTo(out) } }
                            }
                        }
                        if (tree == null && Build.VERSION.SDK_INT >= 29) for (uri in created) contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                        runOnUiThread { result.success(null) }
                    } catch (e: Exception) {
                        for (uri in created) try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                        for (file in local) file.delete()
                        runOnUiThread { result.error("save", e.message, null) }
                    }
                }
                "copyFiles" -> worker.execute {
                    val directory = File(cacheDir, "shared/${UUID.randomUUID()}").apply { mkdirs() }
                    try {
                        val paths = call.argument<List<String>>("paths")!!
                        val uris = paths.mapIndexed { i, path ->
                            val source = File(path)
                            val target = File(directory, "$i/${source.name}").apply { parentFile!!.mkdirs() }
                            source.copyTo(target)
                            FileProvider.getUriForFile(this, "$packageName.shared", target)
                        }
                        runOnUiThread {
                            try {
                                val clip = ClipData.newUri(contentResolver, "Файлы", uris.first())
                                for (uri in uris.drop(1)) clip.addItem(ClipData.Item(uri))
                                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                result.success(null)
                            } catch (e: Exception) { directory.deleteRecursively(); result.error("clipboard", e.message, null) }
                        }
                    } catch (e: Exception) { directory.deleteRecursively(); runOnUiThread { result.error("clipboard", e.message, null) } }
                }
                else -> result.notImplemented()
            }
        }
    }
    private fun mime(file: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 610 && requestCode != 612) return
        val result = picker ?: return
        picker = null
        val source = exportSource; exportSource = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) { result.success(null); return }
        if (requestCode == 610) {
            try { contentResolver.takePersistableUriPermission(uri, data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); result.success(uri.toString()) }
            catch (e: Exception) { result.error("folder", e.message, null) }
        } else worker.execute {
            try { contentResolver.openOutputStream(uri, "w")!!.use { out -> File(source!!).inputStream().use { it.copyTo(out) } }; runOnUiThread { result.success(uri.toString()) } }
            catch (e: Exception) { runOnUiThread { result.error("save", e.message, null) } }
        }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == 611) { permission?.success(grants.isNotEmpty() && grants[0] == PackageManager.PERMISSION_GRANTED); permission = null }
    }
    override fun onResume() {
        super.onResume()
        try { multicast = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).createMulticastLock("transportfiles.discovery").apply { setReferenceCounted(false); acquire() } } catch (_: Exception) {}
    }
    override fun onPause() { multicast?.let { if (it.isHeld) it.release() }; multicast = null; super.onPause() }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
