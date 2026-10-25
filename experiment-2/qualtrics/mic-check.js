// Microphone precheck — Survey 1, on its own page, before the endowment.
//
// Paste the whole file into the JavaScript editor of an empty Text / Graphic question,
// replacing everything Qualtrics pre-fills there. It builds its own markup, so there is
// nothing to type into the question itself.
//
// Writes two Embedded Data fields, both of which must be declared ABOVE this question in
// the Survey Flow or the values do not survive the response:
//   mic_ok         '' while testing, 'yes' on success, 'no' on every failure
//   session_token  a per-response id, for your records only (the platform joins on the
//                  Prolific id, not this)
//
// Branch after this question on mic_ok is not equal to yes -> End of Survey, no code.
//
// On success it clicks Next itself, so nothing may share this page: anything that does
// would be skipped unanswered.

Qualtrics.SurveyEngine.addOnload(function () {
  var q = this, el = document.getElementById(q.questionId);
  if (!el || el.getAttribute('data-micinit')) return;
  el.setAttribute('data-micinit', '1');
  q.hideNextButton();

  var existing = Qualtrics.SurveyEngine.getEmbeddedData('session_token');
  if (!existing) {
    var token = null;
    try { token = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : null; } catch (e) {}
    if (!token) token = 'fh_' + Date.now() + '_' + Math.random().toString(36).slice(2);
    Qualtrics.SurveyEngine.setEmbeddedData('session_token', token);
  }

  el.innerHTML =
    '<p><strong>Microphone check</strong></p>' +
    '<p id="micstat">Test your microphone before continuing. Your browser may ask for permission.</p>' +
    '<button type="button" id="micretry" style="padding:8px 14px;margin-right:8px;">Test microphone</button>' +
    '<button type="button" id="micexit" style="padding:8px 14px;display:none;">I cannot use my microphone</button>' +
    '<p style="font-size:12px;color:#666;">The Salvena portal may ask for microphone permission again because it runs on a different website.</p>';

  var btn = document.getElementById('micretry'),
      exitBtn = document.getElementById('micexit'),
      stat = document.getElementById('micstat');
  var attempt = 0, running = false;

  function say(x) { if (stat) stat.textContent = x; }

  function fail(msg) {
    running = false;
    btn.disabled = false;
    exitBtn.style.display = 'inline-block';
    Qualtrics.SurveyEngine.setEmbeddedData('mic_ok', 'no');
    say(msg);
  }

  function test() {
    if (running) return;
    q.hideNextButton();
    exitBtn.style.display = 'none';
    // Stamps this attempt. A reply from an abandoned attempt arriving late must not be
    // allowed to overwrite the result of the one the participant is actually watching.
    var mine = ++attempt;
    running = true;
    btn.disabled = true;
    Qualtrics.SurveyEngine.setEmbeddedData('mic_ok', '');
    say('Waiting for microphone permission…');

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      fail('This browser cannot provide microphone access. Please try another supported browser or exit the study.');
      return;
    }

    var timer = setTimeout(function () {
      if (mine !== attempt) return;
      attempt++;
      fail('Microphone permission is taking too long. You can retry, or exit the study if microphone access is unavailable.');
    }, 60000);

    navigator.mediaDevices.getUserMedia({ audio: true }).then(function (s) {
      // Released at once. Holding the device would leave the recording indicator lit for
      // the rest of the survey, which reads as being listened to.
      s.getTracks().forEach(function (t) { t.stop(); });
      if (mine !== attempt) return;
      clearTimeout(timer);
      running = false;
      btn.disabled = false;
      Qualtrics.SurveyEngine.setEmbeddedData('mic_ok', 'yes');
      say('Microphone works. Continuing…');
      setTimeout(function () { q.clickNextButton(); }, 600);
    }).catch(function () {
      if (mine !== attempt) return;
      clearTimeout(timer);
      fail('Microphone access failed. Check your browser permission and try again, or exit the study.');
    });
  }

  btn.addEventListener('click', test);

  // Next is hidden and the exit button starts hidden too, so until something happens there
  // is no way off this page. Someone who never clicks Test would otherwise close the tab,
  // and a closed tab is a timed-out Prolific slot rather than a return.
  setTimeout(function () {
    if (Qualtrics.SurveyEngine.getEmbeddedData('mic_ok') !== 'yes') {
      exitBtn.style.display = 'inline-block';
    }
  }, 20000);

  exitBtn.addEventListener('click', function () {
    attempt++;
    running = false;
    Qualtrics.SurveyEngine.setEmbeddedData('mic_ok', 'no');
    q.showNextButton();
    q.clickNextButton();
  });
});
