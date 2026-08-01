package com.settleup.android

import androidx.lifecycle.ViewModel
import com.settleup.android.data.remote.TokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TokenViewModel @Inject constructor(
    val tokenProvider: TokenProvider
) : ViewModel()
