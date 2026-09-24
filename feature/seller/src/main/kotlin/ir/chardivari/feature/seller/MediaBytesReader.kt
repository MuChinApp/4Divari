package ir.chardivari.feature.seller

import android.content.Context
import android.net.Uri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads picked media bytes for upload. Interface keeps the ViewModel free of
 * Android context so publish flow stays unit-testable.
 */
interface MediaBytesReader {
    fun read(uri: String): ByteArray?

    /** ContentResolver mime lookup — null when unknown/unsupported uri. */
    fun mimeTypeOf(uri: String): String?
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SellerBindModule {
    @Binds
    @Singleton
    abstract fun bindMediaBytesReader(
        impl: ContentResolverMediaBytesReader,
    ): MediaBytesReader
}

@Singleton
class ContentResolverMediaBytesReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaBytesReader {
    override fun read(uri: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
    }.getOrNull()

    override fun mimeTypeOf(uri: String): String? = runCatching {
        context.contentResolver.getType(Uri.parse(uri))
    }.getOrNull()
}
