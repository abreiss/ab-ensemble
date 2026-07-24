import { Link, Navigate, Route, Routes } from 'react-router-dom'

import { clearToken } from './api/auth'
import { signalAuthRequired } from './api/http'
import AuthGate from './components/AuthGate'
import AddItem from './routes/AddItem'
import Assemble from './routes/Assemble'
import ItemDetail from './routes/ItemDetail'
import SavedOutfits from './routes/SavedOutfits'
import Stylist from './routes/Stylist'
import WardrobeGrid from './routes/WardrobeGrid'

/**
 * App shell: a persistent header (home link + wardrobe/add affordances) wrapping
 * the routed screens. The stylist is now the landing screen: the routes are the
 * stylist (`/`), the wardrobe grid (`/wardrobe`), add-item (`/add`), item
 * detail (`/item/:id`), and the manual outfit-assembly screen (`/assemble`).
 * The legacy `/style` path redirects to the landing `/` so old bookmarks keep
 * working. The whole shell sits behind `AuthGate`, which renders the passcode
 * screen until a valid session token is stored.
 */
export default function App() {
  // Sign out is a client-side token discard: drop the stored token, then fire the
  // same re-auth signal a 401 uses so AuthGate flips back to the login form. No
  // backend call — the stateless token simply expires at its TTL (Resolved Decision D3).
  function signOut() {
    clearToken()
    signalAuthRequired()
  }

  return (
    <AuthGate>
      <div className="app-shell">
        <header className="app-header">
          <Link to="/" className="app-title">
            Ensemble
          </Link>
          <nav className="app-nav">
            <Link to="/saved" className="btn">
              Saved
            </Link>
            <Link to="/assemble" className="btn">
              Build
            </Link>
            <Link to="/wardrobe" className="btn">
              Wardrobe
            </Link>
            <Link to="/add" className="btn btn-add">
              + Add
            </Link>
            <button type="button" className="btn" onClick={signOut}>
              Sign out
            </button>
          </nav>
        </header>
        <main className="app-main">
          <Routes>
            <Route path="/" element={<Stylist />} />
            <Route path="/wardrobe" element={<WardrobeGrid />} />
            <Route path="/style" element={<Navigate to="/" replace />} />
            <Route path="/add" element={<AddItem />} />
            <Route path="/item/:id" element={<ItemDetail />} />
            <Route path="/assemble" element={<Assemble />} />
            <Route path="/saved" element={<SavedOutfits />} />
          </Routes>
        </main>
      </div>
    </AuthGate>
  )
}
