/**
 * MIT License
 *
 * Copyright (c) [2024] Zuora, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

async function initialize() {
  // Fetch publishable key/profile from server-side config endpoint
  const resp = await fetch('/config');
  let cfg = { publishableKey: '', profile: '' };
  if (resp.ok) {
    try {
      cfg = await resp.json();
    } catch (e) {
      console.error('Failed to parse /config response', e);
    }
  } else {
    console.warn('/config endpoint returned non-ok status; using defaults');
  }

  // Require profile to be provided by server-side config. Fail fast if missing.
  if (!cfg.profile || cfg.profile.trim() === '') {
    console.error('Required configuration `profile` is missing from /config. Aborting initialization.');
    return;
  }

  // Step 1 - Populate Zuora Object
  const publishableKey = cfg.publishableKey || '';
  const zuora = Zuora(publishableKey);

  // Step 2 - Populate HPF configuration
  const configuration = {
  profile: cfg.profile,
    locale: "en",
    region: "US",
    currency: "USD",
    amount: "36.00",
    createPaymentSession: (paymentSessionContext) => {
      console.log("paymentSessionContext: ", paymentSessionContext);
      // generate payment session when end-customer click on the Pay button.
      return new Promise((resolve, reject) => {
        fetch("/create-payment-session", {
          method: "POST",
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            firstName: "John",
            lastName: "Doe",
            currency: "USD",
            address: "123 Main St",
            city: "Denver",
            state: "CO",
            country: "United States",
            zip: "80201",
            email: "test@zuora.io",
            amount: "36.00",
            paymentMethodType: paymentSessionContext.paymentMethodType,
          })
        }).then((response) => {
          if (response.ok) {
            response.json()
                .then((paymentSessionToken) => {
                  resolve(paymentSessionToken);
                })
          }
        }).catch((error) => {
          console.error("Error occurred while creating payment session.")
          console.error(error);
        })
      });
    },
    onComplete: (result) => {
      console.log("==========");
      console.log("Payment Result");
      console.log("==========");
      console.log(`transaction result: ${JSON.stringify(result)}`);
      if (result.success) {
        window.location.href = 'return.html?pid=' + result.paymentId
      } else {
        window.alert("Payment fail: " + result.error?.message);
      }
    }
  };

  // Step 3 - Create and mount payment form
  zuora.createPaymentForm(configuration).then(function(form) {
    form.mount("#zuora-payment-form")
  }).catch(function(error) {
    console.error("Error occurred while creating payment form.")
    console.error(error);
  });

}