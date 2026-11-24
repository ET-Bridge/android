package me.nimd.etbridge.core.data.network

import java.util.Base64
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EndpointsTest {
    @After fun reset() {
        EtlabEndpoints.setInstituteOrigin(null)
        EtlabEndpoints.setRemoteOrigin(null)
    }

    @Test fun defaultsToApi2() {
        assertEquals("https://api2.students.etlab.app/api/v1", EtlabEndpoints.apiBase)
    }

    @Test fun rejectsLegacyForeignAndInsecureOrigins() {
        assertNull(EtlabEndpoints.normalizeOrigin("https://api.students.etlab.app/api/v1"))
        assertNull(EtlabEndpoints.normalizeOrigin("https://studentsappgo.onrender.com"))
        assertNull(EtlabEndpoints.normalizeOrigin("https://evil.example.com"))
        assertNull(EtlabEndpoints.normalizeOrigin("https://etlab.app.evil.com"))
        assertNull(EtlabEndpoints.normalizeOrigin("http://api3.students.etlab.app"))
        assertNull(EtlabEndpoints.normalizeOrigin(""))
        assertEquals("https://api3.students.etlab.app", EtlabEndpoints.normalizeOrigin("https://api3.students.etlab.app/api/v1/"))
    }

    @Test fun instituteOriginBeatsRemoteConfig() {
        EtlabEndpoints.setRemoteOrigin("https://api3.students.etlab.app")
        assertEquals("https://api3.students.etlab.app", EtlabEndpoints.apiOrigin)
        EtlabEndpoints.setInstituteOrigin("https://cev.api.etlab.app")
        assertEquals("https://cev.api.etlab.app", EtlabEndpoints.apiOrigin)
        assertEquals("https://cev.api.etlab.app/uploads/x.pdf", EtlabEndpoints.absolute("uploads/x.pdf"))
    }

    private fun jwt(id: Long, iat: Long, exp: Long): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val body = enc.encodeToString("""{"id":$id,"iat":$iat,"exp":$exp,"type":"student"}""".toByteArray())
        return "$header.$body.sig"
    }

    @Test fun tokenRotationAcceptsOnlyNewerSameStudentUnexpired() {
        val now = 1_800_000_000L
        val current = jwt(7, now - 100, now + 1000)
        assertTrue(TokenRotation.shouldAccept(jwt(7, now, now + 3 * 86400), current, now * 1000))
        assertFalse(TokenRotation.shouldAccept(jwt(8, now, now + 3 * 86400), current, now * 1000)) // other student
        assertFalse(TokenRotation.shouldAccept(jwt(7, now - 200, now + 3 * 86400), current, now * 1000)) // older
        assertFalse(TokenRotation.shouldAccept(jwt(7, now, now - 1), current, now * 1000)) // expired
        assertFalse(TokenRotation.shouldAccept("garbage", current, now * 1000))
        assertFalse(TokenRotation.shouldAccept(current, current, now * 1000))
    }

    @Test fun outageDetection() {
        assertTrue(isOutage(503, "text/html; charset=utf-8"))
        assertTrue(isOutage(502, "application/json"))
        assertTrue(isOutage(500, "text/html"))
        assertFalse(isOutage(500, "application/json"))
        assertFalse(isOutage(401, "application/json"))
    }
}
