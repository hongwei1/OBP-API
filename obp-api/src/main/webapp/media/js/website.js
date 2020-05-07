$(document).ready(function() {
	//fallback for html5 placeholder
	if ( !("placeholder" in document.createElement("input")) ) {
		$("input[placeholder], textarea[placeholder]").each(function() {
			var val = $(this).attr("placeholder");
			if ( this.value == "" ) {
				this.value = val;
			}
			$(this).focus(function() {
				if ( this.value == val ) {
					this.value = "";
				}
			}).blur(function() {
				if ( $.trim(this.value) == "" ) {
					this.value = val;
				}
			})
		});

		// Clear default placeholder values on form submit
		$('form').submit(function() {
			$(this).find("input[placeholder], textarea[placeholder]").each(function() {
				if ( this.value == $(this).attr("placeholder") ) {
					this.value = "";
				}
			});
		});
	}


	// Enforce check of Terms and Conditions (if existing) on signup form
	$('#signup form').submit(function() {
		var agreeTerms = $('#signup #signup-agree-terms input');
		if (agreeTerms.length > 0) {
			if (!agreeTerms.prop('checked')) {
				var msg = 'Please agree to the Terms & Conditions';
				$('#signup #signup-general-error #error').html(msg);
				$('#signup #signup-general-error').removeClass('hide');
				return false;
			}
		}
		return true;
	});

	// Enforce check of Privacy Policy (if existing) on signup form
	$('#signup form').submit(function() {
		var agreePrivacyPolicy = $('#signup #signup-agree-privacy-policy input');
		if (agreePrivacyPolicy.length > 0) {
			if (!agreePrivacyPolicy.prop('checked')) {
				var msg = 'Please agree to the Privacy Policy';
				$('#signup #signup-general-error #error').html(msg);
				$('#signup #signup-general-error').removeClass('hide');
				return false;
			}
		}
		return true;
	});

	// Show sign up errors - FIXME: Change backend to (not) show errors
	var signupError = $('#signup #signup-error #authuser_firstName');
	var txtFirstName = $('#signup #txtFirstName');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		txtFirstName.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}
	var signupError = $('#signup #signup-error #authuser_lastName');
	var txtLastName = $('#signup #txtLastName');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		txtLastName.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}
	var signupError = $('#signup #signup-error #authuser_email');
	var txtEmail = $('#signup #txtEmail');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		txtEmail.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}
	var signupError = $('#signup #signup-error #authuser_username');
	var txtUsername = $('#signup #txtUsername');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		txtUsername.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}
	var signupError = $('#signup #signup-error #authuser_password');
	var signupErrorRepeat = $('#signup #signup-error #authuser_password_repeat');
	var textPassword = $('#signup #textPassword');
	var textPasswordRepeat = $('#signup #textPasswordRepeat');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		signupErrorRepeat.parent().removeClass('hide');
		textPassword.css("border","1px solid #A8000B").css("background","#F9F2F3")
		textPasswordRepeat.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}

	var loginUsernameError = $('#authorise #login-form-username-error');
	var loginUsernameForm = $('#authorise #username');
	if (loginUsernameError.length > 0 && loginUsernameError.html().length > 0) {
		loginUsernameError.parent().removeClass('hide');
		loginUsernameForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	} else{
		loginUsernameError.parent().addClass('hide');
		loginUsernameForm.css("border","").css("background","")
	}

	var loginPasswordError = $('#authorise #login-form-password-error');
	var loginPasswordForm = $('#authorise #password');
	if (loginPasswordError.length > 0 && loginPasswordError.html().length > 0) {
		loginPasswordError.parent().removeClass('hide');
		loginPasswordForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}else{
		loginPasswordError.parent().addClass('hide');
		loginPasswordForm.css("border","").css("background","")
	}

	var consumerRegistrationAppnameError = $('#register-consumer-input #consumer-registration-app-name-error');
	var consumerRegistrationAppnameForm = $('#register-consumer-input #appName');
	if (consumerRegistrationAppnameError.length > 0 && consumerRegistrationAppnameError.html().length > 0) {
		consumerRegistrationAppnameError.parent().removeClass('hide');
		consumerRegistrationAppnameForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}else{
		consumerRegistrationAppnameError.parent().addClass('hide');
		consumerRegistrationAppnameForm.css("border","").css("background","")
	}

	var consumerRegistrationAppDeveloperError = $('#register-consumer-input #consumer-registration-app-developer-error');
	var consumerRegistrationAppDeveloperForm = $('#register-consumer-input #appDev');
	if (consumerRegistrationAppDeveloperError.length > 0 && consumerRegistrationAppDeveloperError.html().length > 0) {
		consumerRegistrationAppDeveloperError.parent().removeClass('hide');
		consumerRegistrationAppDeveloperForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}else{
		consumerRegistrationAppDeveloperError.parent().addClass('hide');
		consumerRegistrationAppDeveloperForm.css("border","").css("background","")
	}

	var consumerRegistrationAppDescError = $('#register-consumer-input #consumer-registration-app-description-error');
	var consumerRegistrationAppDescForm = $('#register-consumer-input #appDesc');
	if (consumerRegistrationAppDescError.length > 0 && consumerRegistrationAppDescError.html().length > 0) {
		consumerRegistrationAppDescError.parent().removeClass('hide');
		consumerRegistrationAppDescForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}else{
		consumerRegistrationAppDescError.parent().addClass('hide');
		consumerRegistrationAppDescForm.css("border","").css("background","")
	}

	var consumerRegistrationAppRedirectUrlError = $('#register-consumer-input #consumer-registration-app-description-error');
	var consumerRegistrationAppRedirectUrlForm = $('#register-consumer-input #appDesc');
	if (consumerRegistrationAppRedirectUrlError.length > 0 && consumerRegistrationAppRedirectUrlError.html().length > 0) {
		consumerRegistrationAppRedirectUrlError.parent().removeClass('hide');
		consumerRegistrationAppRedirectUrlForm.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}else{
		consumerRegistrationAppRedirectUrlError.parent().addClass('hide');
		consumerRegistrationAppRedirectUrlForm.css("border","").css("background","")
	}
	
	showIndicatorCookiePage('cookies-consent');
});
