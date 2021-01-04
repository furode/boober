package no.skatteetaten.aurora.boober.service.openshift.token

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException

private val logger = KotlinLogging.logger {}

/**
 * Loader for the Application Token that will be used when loading resources from Openshift that does not require
 * an authenticated user.
 *
 * @param tokenLocation the location on the file system for the file that contains the token
 * @param tokenOverride an optional override of the token that will be used instead of the one on the file system
 *                      - useful for development and testing.
 */
@Component
class ServiceAccountTokenProvider(
    @Value("\${boober.openshift.tokenLocation}") val tokenLocation: String
) : TokenProvider {

    /**
     * Get the Application Token by using the specified tokenOverride if it is set, or else reads the token from the
     * specified file system path. Any value used will be cached forever, so potential changes on the file system will
     * not be picked up.
     *
     * @return
     */
    override fun getToken() = lazyValue

    val lazyValue: String by lazy {
        readToken()
    }

    private fun readToken(): String {
        logger.info("Reading application token from tokenLocation={}", tokenLocation)
        try {
            val token: String = File(tokenLocation).readText().trim()
            logger.trace(
                "Read token with length={}, firstLetter={}, lastLetter={}",
                token.length,
                token[0],
                token[token.length - 1]
            )
            return token
        } catch (e: IOException) {
            throw IllegalStateException("tokenLocation=$tokenLocation could not be read", e)
        }
    }
}
