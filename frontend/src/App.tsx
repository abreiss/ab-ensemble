import { Link, Navigate, Route, Routes } from 'react-router-dom'

import AuthGate from './components/AuthGate'
import AddItem from './routes/AddItem'
import Assemble from './routes/Assemble'
import ItemDetail from './routes/ItemDetail'
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
  return (
    <AuthGate>
      <div className="app-shell">
        <header className="app-header">
          <Link to="/" className="app-title">
            Ensemble
          </Link>
          <nav className="app-nav">
            <Link to="/wardrobe" className="btn">
              Wardrobe
            </Link>
            <Link to="/add" className="btn btn-add">
              + Add
            </Link>
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
          </Routes>
        </main>
      </div>
    </AuthGate>
  )
}
