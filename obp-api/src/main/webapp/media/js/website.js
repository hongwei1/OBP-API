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
	var textPassword = $('#signup #textPassword');
	var textPasswordRepeat = $('#signup #textPasswordRepeat');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
		textPassword.css("border","1px solid #A8000B").css("background","#F9F2F3")
		textPasswordRepeat.css("border","1px solid #A8000B").css("background","#F9F2F3")
	}
	showIndicatorCookiePage('cookies-consent');
});
