package piuk.blockchain.android.ui.start

import com.blockchain.android.testutils.rxInit
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.nhaarman.mockito_kotlin.whenever

import info.blockchain.wallet.exceptions.DecryptionException
import info.blockchain.wallet.exceptions.HDWalletException
import info.blockchain.wallet.payload.data.Wallet

import org.json.JSONObject
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import io.reactivex.Completable
import io.reactivex.Observable
import okhttp3.ResponseBody
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.utils.PrefsUtil
import retrofit2.Response

import junit.framework.TestCase.assertFalse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.amshove.kluent.any
import org.amshove.kluent.mock
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import piuk.blockchain.android.R
import piuk.blockchain.androidcore.utils.PersistentPrefs

class ManualPairingPresenterTest {

    private lateinit var subject: ManualPairingPresenter

    private val view: ManualPairingView = mock()
    private val appUtil: AppUtil = mock()
    private val authDataManager: AuthDataManager = mock()
    private val payloadDataManager: PayloadDataManager = mock()
    private val wallet: Wallet = mock()
    private val prefsUtil: PrefsUtil = mock()
    private val analytics: Analytics = mock()

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    @Before
    fun setUp() {
        subject = ManualPairingPresenter(
            appUtil,
            authDataManager,
            payloadDataManager,
            prefsUtil,
            analytics
        )
        subject.attachView(view)

        whenever(view.guid).thenReturn(GUID)
        whenever(view.password).thenReturn(PASSWORD)
        whenever(wallet.guid).thenReturn(GUID)
        whenever(wallet.sharedKey).thenReturn("shared_key")
        whenever(payloadDataManager.wallet).thenReturn(wallet)
    }

    /**
     * Password is missing, should trigger [ManualPairingActivity.showToast]
     */
    @Test
    fun onContinueClickedNoPassword() {
        // Arrange
        whenever(view.password).thenReturn("")

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view).showToast(any(), any())
        verify(analytics, never()).logEvent(any())
    }

    /**
     * GUID is missing, should trigger [ManualPairingActivity.showToast]
     */
    @Test
    fun onContinueClickedNoGuid() {
        // Arrange
        whenever(view.guid).thenReturn("")

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view).showToast(any(), any())
        verify(analytics, never()).logEvent(any())
    }

    /**
     * Password is correct, should trigger [ManualPairingActivity.goToPinPage]
     */
    @Test
    fun onContinueClickedCorrectPassword() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        val responseBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.success(responseBody)
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.just("1234567890"))
        whenever(payloadDataManager.initializeFromPayload(anyString(), anyString()))
            .thenReturn(Completable.complete())

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view).goToPinPage()
        verify(prefsUtil).setValue(PersistentPrefs.KEY_WALLET_GUID, GUID)
        verify(prefsUtil).setValue(PersistentPrefs.KEY_EMAIL_VERIFIED, true)
        verify(appUtil).sharedKey = any()
        verify(analytics).logEvent(AnalyticsEvents.WalletManualLogin)
    }

    /**
     * Password is correct but 2FA is enabled, should trigger [ManualPairingActivity.showTwoFactorCodeNeededDialog]
     */
    @Test
    fun onContinueClickedCorrectPasswordTwoFa() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        val responseBody = TWO_FA_RESPONSE.toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.success(responseBody)
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.just("1234567890"))
        whenever(payloadDataManager.initializeFromPayload(anyString(), anyString()))
            .thenReturn(Completable.complete())

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view).dismissProgressDialog()
        verify(view).showTwoFactorCodeNeededDialog(
            any(),
            any(),
            any(),
            any(),
            any()
        )
    }

    /**
     * AuthDataManager returns a failure when getting encrypted payload, should trigger [ ][ManualPairingActivity.showToast]
     */
    @Test
    fun onContinueClickedPairingFailure() {
        // Arrange
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.error(Throwable()))
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.just("1234567890"))

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view).showToast(any(), any())
        verify(view).resetPasswordField()
        verify(view).dismissProgressDialog()
        verify(analytics, never()).logEvent(any())
    }

    /**
     * AuthDataManager returns failure when polling auth status, should trigger [ ][ManualPairingActivity.showToast]
     */
    @Ignore("This has never actually worked, but refactoring has highlighted the failure")
    @Test
    fun onContinueClickedCreateFailure() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        val responseBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.success(responseBody)
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.error(Throwable()))
        // Act
        subject.onContinueClicked()
        // Assert

        verify(view).showToast(any(), any())
        verify(view).resetPasswordField()
        verify(view).dismissProgressDialog()
        verify(analytics, never()).logEvent(any())
    }

    /**
     * AuthDataManager returns a [DecryptionException], should trigger [ ][ManualPairingActivity.showToast].
     */
    @Test
    fun onContinueClickedDecryptionFailure() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        val responseBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.success(responseBody)
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.just("1234567890"))
        whenever(payloadDataManager.initializeFromPayload(anyString(), anyString()))
            .thenReturn(Completable.error(DecryptionException()))

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view).showToast(any(), any())
        verify(view).resetPasswordField()
        verify(view).dismissProgressDialog()
        verify(analytics, never()).logEvent(any())
    }

    /**
     * AuthDataManager returns a [HDWalletException], should trigger [ ][ManualPairingActivity.showToast].
     */
    @Test
    fun onContinueClickedHDWalletExceptionFailure() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        val responseBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.success(responseBody)
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.just("1234567890"))
        whenever(payloadDataManager.initializeFromPayload(anyString(), anyString()))
            .thenReturn(Completable.error(HDWalletException()))

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view).showToast(any(), any())
        verify(view).resetPasswordField()
        verify(view).dismissProgressDialog()
        verify(analytics, never()).logEvent(any())
    }

    /**
     * AuthDataManager returns a fatal exception, should restart the app and clear credentials.
     */
    @Test
    fun onContinueClickedFatalErrorClearData() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        val responseBody = "{}".toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.success(responseBody)
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.just("1234567890"))
        whenever(payloadDataManager.initializeFromPayload(anyString(), anyString()))
            .thenReturn(Completable.error(RuntimeException()))

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view).showToast(any(), any())
        verify(view).resetPasswordField()
        verify(view).dismissProgressDialog()
        verify(appUtil).clearCredentialsAndRestart(LauncherActivity::class.java)
    }

    /**
     * AuthDataManager returns an error when getting session ID, should trigger
     * AppUtil#clearCredentialsAndRestart()
     */
    @Test
    fun onContinueClickedFatalError() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString()))
            .thenReturn(Observable.error(Throwable()))
        // Act
        subject.onContinueClicked()
        // Assert

        verify(view).showToast(any(), any())
        verify(view).resetPasswordField()
        verify(view).dismissProgressDialog()
    }

    /**
     * [AuthDataManager.getEncryptedPayload] throws exception. Should restart
     * the app via AppUtil#clearCredentialsAndRestart()
     */
    @Test
    fun onContinueClickedEncryptedPayloadFailure() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.error(Throwable()))
        // Act
        subject.onContinueClicked()
        // Assert

        verify(view).showToast(any(), any())
        verify(view).resetPasswordField()
        verify(view).dismissProgressDialog()
        verify(analytics, never()).logEvent(any())
    }

    /**
     * [AuthDataManager.startPollingAuthStatus] returns Access Required.
     * Should restart the app via AppUtil#clearCredentialsAndRestart()
     */
    @Test
    fun onContinueClickedWaitingForAuthRequired() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        val responseBody = KEY_AUTH_REQUIRED_JSON.toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.error<ResponseBody>(500, responseBody)
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.just(ManualPairingPresenter.KEY_AUTH_REQUIRED))
        whenever(authDataManager.createCheckEmailTimer()).thenReturn(Observable.just(1))
        // Act
        subject.onContinueClicked()
        // Assert

        verify(view).showToast(any(), any())
        verify(view).resetPasswordField()
        verify(analytics, never()).logEvent(any())
    }

    /**
     * [AuthDataManager.startPollingAuthStatus] returns payload. Should
     * attempt to decrypt the payload.
     */
    @Test
    fun onContinueClickedWaitingForAuthSuccess() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        val responseBody = KEY_AUTH_REQUIRED_JSON.toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.error<ResponseBody>(500, responseBody)
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.just("{}"))
        whenever(authDataManager.createCheckEmailTimer()).thenReturn(Observable.just(1))
        whenever(payloadDataManager.initializeFromPayload(anyString(), anyString()))
            .thenReturn(Completable.complete())
        whenever(wallet.guid).thenReturn(GUID)
        whenever(wallet.sharedKey).thenReturn("shared_key")

        // Act
        subject.onContinueClicked()

        // Assert
        verify(payloadDataManager).initializeFromPayload(any(), any())
        verify(analytics).logEvent(AnalyticsEvents.WalletManualLogin)
    }

    /**
     * [AuthDataManager.createCheckEmailTimer] throws an error. Should show error toast.
     */
    @Test
    fun onContinueClickedWaitingForAuthEmailTimerError() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        val responseBody = KEY_AUTH_REQUIRED_JSON.toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.error<ResponseBody>(500, responseBody)
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.just("{}"))
        whenever(authDataManager.createCheckEmailTimer())
            .thenReturn(Observable.error(Throwable()))
        whenever(payloadDataManager.initializeFromPayload(anyString(), anyString()))
            .thenReturn(Completable.complete())

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view).showToast(any(), any())
        verify(view).resetPasswordField()
    }

    /**
     * [AuthDataManager.startPollingAuthStatus] returns an error. Should
     * restart the app via AppUtil#clearCredentialsAndRestart()
     */
    @Test
    fun onContinueClickedWaitingForAuthFailure() {
        // Arrange
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))

        val responseBody = KEY_AUTH_REQUIRED_JSON.toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.error<ResponseBody>(500, responseBody)

        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.createCheckEmailTimer()).thenReturn(Observable.just(1))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.error(Throwable()))

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view).showToast(any(), any())
        verify(view).resetPasswordField()
        verify(analytics, never()).logEvent(any())
    }

    /**
     * [AuthDataManager.startPollingAuthStatus] counts down to zero. Should
     * restart the app via AppUtil#clearCredentialsAndRestart()
     */
    @Test
    fun onContinueClickedWaitingForAuthCountdownComplete() {
        whenever(authDataManager.getSessionId(anyString())).thenReturn(Observable.just("1234567890"))
        val responseBody = KEY_AUTH_REQUIRED_JSON.toResponseBody("application/json".toMediaTypeOrNull())
        val response = Response.error<ResponseBody>(500, responseBody)
        whenever(authDataManager.getEncryptedPayload(anyString(), anyString()))
            .thenReturn(Observable.just(response))
        whenever(authDataManager.createCheckEmailTimer()).thenReturn(Observable.just(0))
        whenever(authDataManager.startPollingAuthStatus(anyString(), anyString()))
            .thenReturn(Observable.just("1234567890"))

        // Act
        subject.onContinueClicked()

        // Assert
        verify(view, times(2)).showToast(any(), any())
        verify(view, times(2)).resetPasswordField()
        verify(appUtil).clearCredentialsAndRestart(LauncherActivity::class.java)
        verify(analytics, never()).logEvent(any())
    }

    @Test
    fun submitTwoFactorCodeNull() {
        // Arrange
        val responseObject = JSONObject()
        val sessionId = "SESSION_ID"
        // Act
        subject.submitTwoFactorCode(responseObject, sessionId, GUID, PASSWORD, null)
        // Assert
        verify(view).showToast(R.string.two_factor_null_error, ToastCustom.TYPE_ERROR)
        verify(analytics, never()).logEvent(any())
    }

    @Test
    fun submitTwoFactorCodeFailed() {
        // Arrange
        val responseObject = JSONObject()
        val sessionId = "SESSION_ID"
        val code = "123456"
        whenever(authDataManager.submitTwoFactorCode(sessionId, GUID, code))
            .thenReturn(Observable.error(Throwable()))
        // Act
        subject.submitTwoFactorCode(responseObject, sessionId, GUID, PASSWORD, code)
        // Assert
        verify(view).showProgressDialog(R.string.please_wait, null)
        verify(view, atLeastOnce()).dismissProgressDialog()
        verify(view).showToast(R.string.two_factor_incorrect_error, ToastCustom.TYPE_ERROR)
        verify(authDataManager).submitTwoFactorCode(sessionId, GUID, code)
    }

    @Test
    fun submitTwoFactorCodeSuccess() {
        // Arrange
        val responseObject = JSONObject()
        val sessionId = "SESSION_ID"
        val code = "123456"
        whenever(authDataManager.submitTwoFactorCode(sessionId, GUID, code))
            .thenReturn(
                Observable.just(
                    TWO_FA_RESPONSE.toResponseBody("application/json".toMediaTypeOrNull())
                )
            )
        whenever(payloadDataManager.initializeFromPayload(anyString(), anyString()))
            .thenReturn(Completable.complete()
        )

        // Act
        subject.submitTwoFactorCode(responseObject, sessionId, GUID, PASSWORD, code)

        // Assert
        verify(view).showProgressDialog(R.string.please_wait, null)
        verify(view, atLeastOnce()).dismissProgressDialog()
        verify(view).goToPinPage()
        verify(authDataManager).submitTwoFactorCode(sessionId, GUID, code)
        verify(payloadDataManager).initializeFromPayload(TWO_FA_PAYLOAD, PASSWORD)
        verify(analytics).logEvent(AnalyticsEvents.WalletManualLogin)
    }

    @Test
    fun onProgressCancelled() {
        // Arrange

        // Act
        subject.onProgressCancelled()
        // Assert
        assertFalse(subject.waitingForAuth)
        assertEquals(0, subject.compositeDisposable.size().toLong())
    }

    companion object {
        private const val KEY_AUTH_REQUIRED_JSON = "{\"authorization_required\": true}"
        private const val TWO_FA_RESPONSE = "{auth_type: 5}"
        private const val TWO_FA_PAYLOAD = "{\"payload\":\"{auth_type: 5}\"}"

        private const val GUID = "1234567890"
        private const val PASSWORD = "PASSWORD"
    }
}
