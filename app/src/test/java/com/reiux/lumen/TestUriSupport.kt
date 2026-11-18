package android.net

import android.os.Parcel

/**
 * Minimal JVM unit-test Uri implementation for testing production methods
 * that accept [android.net.Uri] when `unitTests.returnDefaultValues = true`
 * stubs out [Uri.parse].
 */
class TestUri(
    private val raw: String,
    private val schemeValue: String?,
    private val authorityValue: String?,
    private val pathValue: String? = null
) : Uri() {
    override fun getScheme(): String? = schemeValue
    override fun getAuthority(): String? = authorityValue
    override fun getEncodedAuthority(): String? = authorityValue
    override fun getPath(): String? = pathValue
    override fun getEncodedPath(): String? = pathValue
    override fun getPathSegments(): List<String> =
        pathValue?.split('/')?.filter { it.isNotEmpty() } ?: emptyList()
    override fun getLastPathSegment(): String? = pathSegments.lastOrNull()
    override fun getHost(): String? = authorityValue?.substringBefore(':')
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getSchemeSpecificPart(): String = raw.substringAfter(':', "")
    override fun getEncodedSchemeSpecificPart(): String = schemeSpecificPart
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = schemeValue == null
    override fun buildUpon(): Builder = throw UnsupportedOperationException("Not needed in unit tests")
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}
    override fun toString(): String = raw
    override fun equals(other: Any?): Boolean = other is TestUri && raw == other.raw
    override fun hashCode(): Int = raw.hashCode()
}
