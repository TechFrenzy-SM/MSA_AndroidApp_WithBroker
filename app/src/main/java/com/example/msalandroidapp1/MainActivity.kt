package com.example.msalandroidapp1

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.AuthenticationResult
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.helper.BrokerHelperActivity
import org.json.JSONObject
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var btnAppMetadata: Button? = null
    private var btnLogin: Button? = null
    private var btnLogout: Button? = null
    private var btnCallGraph: Button? = null
    private var tvUsername: TextView? = null
    private var tvUserDetails: TextView? = null
    private var tvLog: TextView? = null

    private var msalApp: ISingleAccountPublicClientApplication? = null
    private var msalAccount: IAccount? = null
    private var lastAuthenticationResult: IAuthenticationResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnAppMetadata = findViewById(R.id.btn_msalMetadataInfo)
        btnLogin = findViewById(R.id.btn_signIn)
        btnLogout = findViewById(R.id.btn_signOut)
        tvUsername = findViewById(R.id.tv_username)
        tvUserDetails = findViewById(R.id.tv_user_details)
        btnCallGraph = findViewById(R.id.btn_callGraph)
        tvLog = findViewById(R.id.tv_log)

        tvUsername?.isVisible = false
        tvUserDetails?.isVisible = false

        tvUserDetails?.movementMethod = ScrollingMovementMethod.getInstance()
        tvLog?.movementMethod = ScrollingMovementMethod.getInstance()


        // Initialize MSAL
        // Creates a PublicClient Application using MSAL configuration for SingleAccount access.                                                                                             
        PublicClientApplication.createSingleAccountPublicClientApplication(
            this,
            R.raw.msal_auth_config,
            object: IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication?) {
                    msalApp = application
                    loadAccount()
                }
                override fun onError(exception: MsalException?) {
                    println(exception.toString())
                    tvLog?.text = exception.toString()
                }
            }
        )
    }

    private fun initializeUI() {
        btnAppMetadata?.setOnClickListener {
            val intent = Intent(this, BrokerHelperActivity::class.java)
            startActivity(intent)
        }

        /*btnLogin?.setOnClickListener {
            msalApp!!.signIn(this, null, arrayOf("user.read"), getAuthCallback())
        }*/

        btnLogin?.setOnClickListener {
            val signInParameters = SignInParameters.builder()
                .withActivity(this)
                .withScopes(arrayOf("user.read").toList())
                .withCallback(getAuthCallback())

            msalApp!!.signIn(signInParameters.build())
        }


        btnLogout?.setOnClickListener {
            msalApp!!.signOut(
                object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() {
                        updateUI(null)
                        signOut()
                    }
                    override fun onError(exception: MsalException) {
                        tvLog?.text = exception.toString()
                    }
                }
            )
        }

        btnCallGraph?.setOnClickListener {
            val silentParameters = AcquireTokenSilentParameters.Builder()
                .withScopes(arrayOf("user.read").toList())
                .fromAuthority(msalApp!!.configuration.defaultAuthority.authorityUri.toString())
                .withCallback(getAuthCallback())
                .forAccount(msalAccount)
            msalApp!!.acquireTokenSilentAsync(silentParameters.build())
        }

    }

    override fun onResume() {
        super.onResume()
        initializeUI()
        loadAccount()
    }

    private fun loadAccount() {
        if(msalApp == null) {
            return
        }

        // Get the current sign-in account and notifies if the account changes.
        msalApp!!.getCurrentAccountAsync(
            object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) {
                    msalAccount = activeAccount
                    updateUI(msalAccount)
                }
                override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                    if(currentAccount == null) {
                        msalAccount = null
                        signOut()
                    }
                }
                override fun onError(exception: MsalException) {
                    println(exception.toString())
                    tvLog?.text = exception.toString()
                }
            })
    }

    private fun getAuthCallback(): AuthenticationCallback {
        return object : AuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult?) {
                lastAuthenticationResult = authenticationResult
                msalAccount = authenticationResult?.account
                updateUI(msalAccount)

                if (authenticationResult != null) {
                    callGraphAPI(authenticationResult)
                }
            }
            override fun onError(exception: MsalException?) {
                tvLog?.text = exception.toString()
            }
            override fun onCancel() {
                println("User Cancelled!")
            }
        }
    }

    private fun updateUI(account: IAccount?) {

        if(account != null) {
            btnLogin?.isEnabled = false
            btnLogout?.isEnabled = true
            btnCallGraph?.isEnabled = true

            tvUsername?.isVisible = true
            tvUsername?.text = "User: " + account.getClaims()?.get("name").toString()

            var claims = account.getClaims()
            val claimsText = StringBuilder()

            val claimMappings = mapOf(
                "name" to "Name",
                "preferred_username" to "Email",
                "oid" to "User Id"
            )

            if (claims != null) {

                for((claimName, claimValue) in claims) {
                    println("Claim: $claimName, Value: $claimValue")
                    claimsText.append("$claimName : $claimValue\n")

                    if(claimMappings.containsKey(claimName)) {
                        val formattedClaimName = claimMappings[claimName]
                        claimsText.append("• $formattedClaimName : $claimValue\n")
                    }
                }
            }

            println("Authentication Result: $lastAuthenticationResult")

            Toast.makeText(this, "You have successfully logged in!", Toast.LENGTH_SHORT).show()
            tvUserDetails?.isVisible = true
            tvUserDetails?.text = "Claims from ID-Token: \n" + claimsText.toString()
            tvLog?.text = ""

        } else {
            btnLogin?.isEnabled = true
            btnLogout?.isEnabled = false
            btnCallGraph?.isEnabled = false
            tvUserDetails?.isVisible = false
        }
    }

    private fun signOut() {
        Toast.makeText(this, "You have successfully logged out!", Toast.LENGTH_SHORT).show()
        tvUsername?.text = ""
        tvUsername?.isVisible = false
        tvUserDetails?.text = ""
        tvLog?.text = ""
    }

    private fun callGraphAPI(authenticationResult: IAuthenticationResult) {
        println("Access-Token:\n${authenticationResult.accessToken}")


        val queue = Volley.newRequestQueue(this)
        val request = object : JsonObjectRequest(
            Method.GET,
            "https://graph.microsoft.com/beta/me",
            JSONObject(),
            {response ->
                tvUserDetails?.isVisible = true
                tvUserDetails?.text = "Response from Graph API:\n${response}."
                tvLog?.text = "Status: 200 (OK)"
            },
            {error ->
                if (error.networkResponse != null && error.networkResponse.data != null) {
                    val errorResponse = String(error.networkResponse.data, Charsets.UTF_8)
                    tvLog?.text = "Error Response:\n$errorResponse"
                } else {
                    tvLog?.text = "Unknown Error"
                }
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Authorization"] = "Bearer " + authenticationResult.accessToken
                return headers
            }
        }
        queue.add(request)
    }
}