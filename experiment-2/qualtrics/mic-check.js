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
//
// TEST IT ON THE PUBLISHED LINK, NOT IN PREVIEW. Qualtrics preview renders the survey
// inside an iframe, and a browser refuses microphone access to an iframe that was not
// given allow="microphone" — so in preview the check fails however the participant
// answers the prompt. The script says so on screen when it detects that case.

Qualtrics.SurveyEngine.addOnload(function () {
  // Only the button hiding belongs here. The DOM work is in addOnReady, because in several
  // themes the question is not in the document yet at addOnload and every lookup returns
  // null — which is a check that silently does nothing at all.
  this.hideNextButton();
});

Qualtrics.SurveyEngine.addOnReady(function () {
  var q = this;

  var container = null;
  try { container = q.getQuestionContainer(); } catch (e) {}
  if (!container) container = document.getElementById(q.questionId);
  if (!container) {
    // Cannot draw the check. Showing Next is the only safe failure: a hidden Next with
    // nothing to click is a participant with no way out of the study.
    Qualtrics.SurveyEngine.setEmbeddedData('mic_ok', 'no');
    q.showNextButton();
    return;
  }
  if (container.getAttribute('data-micinit')) return;
  container.setAttribute('data-micinit', '1');

  var existing = Qualtrics.SurveyEngine.getEmbeddedData('session_token');
  if (!existing) {
    var token = null;
    try { token = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : null; } catch (e) {}
    if (!token) token = 'fh_' + Date.now() + '_' + Math.random().toString(36).slice(2);
    Qualtrics.SurveyEngine.setEmbeddedData('session_token', token);
  }

  // Replaces the question's own text, leaving Qualtrics' wrapper alone where it exists.
  var target = container.querySelector('.QuestionText') || container;
  target.innerHTML =
    '<p><strong>Microphone check</strong></p>' +
    '<p id="micstat">Test your microphone before continuing. Your browser may ask for permission.</p>' +
    '<button type="button" id="micretry" style="padding:8px 14px;margin-right:8px;">Test microphone</button>' +
    '<button type="button" id="micexit" style="padding:8px 14px;display:none;">I cannot use my microphone</button>' +
    '<p style="font-size:12px;color:#666;">The Salvena portal may ask for microphone permission again because it runs on a different website.</p>';

  var btn = target.querySelector('#micretry'),
      exitBtn = target.querySelector('#micexit'),
      stat = target.querySelector('#micstat');
  var attempt = 0, running = false;

  // True in Qualtrics preview, false on the published link.
  var framed = false;
  try { framed = (window.self !== window.top); } catch (e) { framed = true; }

  function say(x) { if (stat) stat.textContent = x; }

  function fail(msg) {
    running = false;
    btn.disabled = false;
    exitBtn.style.display = 'inline-block';
    Qualtrics.SurveyEngine.setEmbeddedData('mic_ok', 'no');
    if (framed) {
      msg += ' (This survey is running inside a preview frame, which browsers never allow ' +
             'microphone access to. Open the published survey link instead.)';
    }
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
    }).catch(function (err) {
      if (mine !== attempt) return;
      clearTimeout(timer);
      // The error name is on screen deliberately: NotAllowedError is a refusal,
      // NotFoundError is no device at all, and the two need different advice.
      var name = (err && err.name) ? err.name : 'unknown error';
      fail('Microphone access failed (' + name + '). Check your browser permission and try again, or exit the study.');
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
