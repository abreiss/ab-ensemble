import { Link, Route, Routes } from 'react-router-dom'

import AddItem from './routes/AddItem'
import ItemDetail from './routes/ItemDetail'
import WardrobeGrid from './routes/WardrobeGrid'

/**
 * App shell: a persistent header (home link + add affordance) wrapping the routed
 * screens. Mobile-first single column; the routes are the wardrobe grid (`/`),
 * add-item (`/add`), and item detail (`/item/:id`).
 */
export default function App() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/" className="app-title">
          Ensemble
        </Link>
        <Link to="/add" className="btn btn-add">
          + Add
        </Link>
      </header>
      <main className="app-main">
        <Routes>
          <Route path="/" element={<WardrobeGrid />} />
          <Route path="/add" element={<AddItem />} />
          <Route path="/item/:id" element={<ItemDetail />} />
        </Routes>
      </main>
    </div>
  )
}
