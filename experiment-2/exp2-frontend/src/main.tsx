import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import '@insurance-portal/core/theme.scss'
import 'bootstrap-icons/font/bootstrap-icons.css'
import '@insurance-portal/core/index.css'
import { App } from '@insurance-portal/core'
import StudyGate from './study/StudyGate.tsx'
import StudyChrome from './study/StudyChrome.tsx'
import DataPage from './study/DataPage.tsx'
import VoiceOnlyPage from './study/VoiceOnlyPage.tsx'
import StudySplit from './study/StudySplit.tsx'
import VoiceWidget from './voice/VoiceWidget'
import { configurePortalHost, type InteractionMode } from '@insurance-portal/core/host'
import { getLevel, type Level } from './study/level'
import { participantHeaders } from './study/headers'
import { getCondition } from './study/session'
import { logEvent } from './study/log'

/**
 * The study's independent variable, translated into the three states the portal understands.
 * The portal never learns the level itself — that is the whole point: it behaves the same for any
 * host that puts it in the same mode.
 *
 *   0 no bot, 1 bot advises  -> the participant operates the portal
 *   2 bot operates the GUI   -> read-only to the participant; the bot drives it
 *   3 voice only             -> no portal GUI at all
 */
function modeForLevel(level: Level): InteractionMode {
  if (level === 3) return 'headless'
  if (level === 2) return 'readonly'
  return 'interactive'
}

// Composition root: the study hosts the portal. Everything study-specific the portal needs is
// handed over here, so no portal module imports study/ directly. Configured before render; every
// entry is a function, evaluated when the portal asks, so the condition only has to be resolved by
// the time the portal renders (which StudyGate guarantees) rather than right now.
configurePortalHost({
  apiHeaders: participantHeaders,
  accountRef: () => getCondition()?.participantId ?? null,
  interactionMode: () => modeForLevel(getLevel()),
  onEvent: logEvent,
  headlessView: () => <VoiceOnlyPage />,
  navbarChrome: () => <StudyChrome />,
  // Level 0 = no voice; the widget appears for levels 1-3.
  assistant: () => (getLevel() >= 1 ? <VoiceWidget /> : null),
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      {/*
        The study's own routes sit above the portal's. /data is the researcher's behavioural export
        — study data, study page — so it is served here rather than being a portal route that
        happens to call a study endpoint. It is outside StudyGate on purpose: opening it must not
        create a participant session.
      */}
      <Routes>
        <Route path="/data" element={<DataPage />} />
        <Route
          path="*"
          element={
            <StudyGate>
              {/*
                The task card sits beside the portal for every level, including 3 (where the left
                pane holds the voice-only screen instead of the portal GUI). Inside the gate, so it
                appears only once a condition is resolved - and never on /data, which is routed
                above and is the researcher's page, not a participant's.
              */}
              <StudySplit>
                <App />
              </StudySplit>
            </StudyGate>
          }
        />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
