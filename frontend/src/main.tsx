import React from 'react'
import ReactDOM from 'react-dom/client'
import App from '@/App'
// Polices auto-hebergees : la CSP n'autorise que font-src 'self'.
import '@fontsource/zen-kaku-gothic-new/latin-400.css'
import '@fontsource/zen-kaku-gothic-new/latin-500.css'
import '@fontsource/zen-kaku-gothic-new/latin-700.css'
import '@fontsource/dela-gothic-one/latin-400.css'
import '@/index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
