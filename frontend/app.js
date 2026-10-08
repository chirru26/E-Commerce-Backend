const API_BASE = "http://localhost:8080";
const state = { token: localStorage.getItem("shoplite.accessToken"), refreshToken: localStorage.getItem("shoplite.refreshToken"), user: JSON.parse(localStorage.getItem("shoplite.user") || "null"), category: "", query: "", page: 0, totalPages: 1, products: [] };
const $ = id => document.getElementById(id);

async function api(path, options = {}, retry = true) {
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (state.token) headers.set("Authorization", `Bearer ${state.token}`);
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (response.status === 401 && retry && !path.includes("/auth/")) {
    if (state.refreshToken && await refreshSession()) {
      return api(path, options, false);
    } else {
      clearSession();
      return api(path, options, false);
    }
  }
  if (!response.ok) {
    let detail = `Request failed (${response.status})`;
    try { const body = await response.json(); detail = body.message || body.error || detail } catch { }
    throw new Error(detail);
  }
  if (response.status === 204) return null;
  return response.json();
}
async function refreshSession() {
  try {
    const result = await fetch(`${API_BASE}/api/v1/auth/refresh`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refreshToken: state.refreshToken }) });
    if (!result.ok) throw new Error("Session expired");
    saveSession(await result.json()); return true;
  } catch { clearSession(); return false }
}
function saveSession(data) {
  state.token = data.accessToken; state.refreshToken = data.refreshToken; state.user = { userId: data.userId, email: data.email, role: data.role };
  localStorage.setItem("shoplite.accessToken", state.token); localStorage.setItem("shoplite.refreshToken", state.refreshToken); localStorage.setItem("shoplite.user", JSON.stringify(state.user)); renderAuthState();
}
function clearSession() {
  state.token = null; state.refreshToken = null; state.user = null;
  localStorage.removeItem("shoplite.accessToken"); localStorage.removeItem("shoplite.refreshToken"); localStorage.removeItem("shoplite.user"); renderAuthState();
}
function money(value, currency = "INR") {
  try { return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(value) } catch { return `${currency} ${value}` }
}
function showMessage(message) { $("message").textContent = message; $("message").classList.remove("hidden") }
function hideMessage() { $("message").classList.add("hidden") }

async function checkApi() {
  try { await fetch(`${API_BASE}/actuator/health`); $("apiStatus").textContent = "Online"; $("apiStatusText").textContent = "Gateway + Spring Boot are reachable" }
  catch { $("apiStatus").textContent = "Offline"; $("apiStatusText").textContent = "Start Docker Compose or the gateway" }
}
async function loadCategories() {
  const categories = await api("/api/v1/categories"), list = $("categoryList");
  list.innerHTML = '<button class="category-chip active" data-category="">All products</button>';
  for (const category of categories) {
    const button = document.createElement("button"); button.className = "category-chip"; button.dataset.category = category.slug; button.textContent = category.name; list.appendChild(button);
  }
  list.querySelectorAll(".category-chip").forEach(button => button.addEventListener("click", () => {
    state.category = button.dataset.category; state.page = 0; list.querySelectorAll(".category-chip").forEach(b => b.classList.toggle("active", b === button)); loadProducts(false);
  }));
}
async function loadProducts(append = false) {
  hideMessage();
  const params = new URLSearchParams({ page: String(state.page), size: "12" });
  if (state.query) params.set("q", state.query); if (state.category) params.set("category", state.category);
  try {
    const data = await api(`/api/v1/products?${params}`);
    state.totalPages = data.totalPages; state.products = append ? [...state.products, ...data.content] : data.content; renderProducts();
    $("loadMore").classList.toggle("hidden", state.page + 1 >= state.totalPages);
  } catch (error) { showMessage(error.message); $("productGrid").innerHTML = "" }
}
function renderProducts() {
  const grid = $("productGrid"); grid.innerHTML = "";
  if (!state.products.length) { grid.innerHTML = '<div class="empty">No products found.</div>'; return }
  for (const product of state.products) {
    const card = document.createElement("article"); card.className = "product-card";
    card.innerHTML = `<div class="product-art">🛍️</div><div class="product-body"><span class="product-category">${escapeHtml(product.categoryName || "Product")}</span><h3>${escapeHtml(product.name)}</h3><p class="product-description">${escapeHtml(product.description || "No description available.")}</p><div class="product-footer"><span class="price">${money(product.price, product.currency)}</span><button class="button button-primary add-cart" data-id="${product.id}">Add</button></div></div>`;
    card.querySelector(".add-cart").addEventListener("click", () => addToCart(product.id)); grid.appendChild(card);
  }
}
async function addToCart(productId) {
  if (!state.token) { openAuth(); return }
  try { await api("/api/v1/cart/items", { method: "POST", body: JSON.stringify({ productId, quantity: 1 }) }); await loadCart(); openCart() }
  catch (error) { showMessage(error.message) }
}
async function loadCart() {
  if (!state.token) { $("cartCount").textContent = "0"; return }
  try { renderCart(await api("/api/v1/cart")) } catch { clearSession(); $("cartCount").textContent = "0" }
}
function renderCart(cart) {
  $("cartCount").textContent = String(cart.itemCount || 0); const content = $("cartContent");
  if (!cart.items?.length) { content.innerHTML = '<div class="empty">Your cart is empty.</div>'; return }
  content.innerHTML = cart.items.map(item => `<div class="cart-item"><div><h4>${escapeHtml(item.name)}</h4><div class="cart-meta">${money(item.unitPrice, item.currency)} each</div><div class="qty"><button data-action="minus" data-id="${item.productId}">−</button><strong>${item.quantity}</strong><button data-action="plus" data-id="${item.productId}">+</button><button data-action="remove" data-id="${item.productId}">Remove</button></div></div><strong>${money(item.lineTotal, item.currency)}</strong></div>`).join("") + `<div class="cart-summary"><span>Subtotal</span><span>${money(cart.subtotal, cart.currency || "INR")}</span></div>`;
  content.querySelectorAll("[data-action]").forEach(button => button.addEventListener("click", async () => {
    const item = cart.items.find(x => x.productId === button.dataset.id); if (!item) return;
    try {
      if (button.dataset.action === "remove") await api(`/api/v1/cart/items/${item.productId}`, { method: "DELETE" });
      else { const quantity = item.quantity + (button.dataset.action === "plus" ? 1 : -1); if (quantity <= 0) await api(`/api/v1/cart/items/${item.productId}`, { method: "DELETE" }); else await api(`/api/v1/cart/items/${item.productId}`, { method: "PUT", body: JSON.stringify({ quantity }) }) }
      await loadCart();
    } catch (error) { showMessage(error.message) }
  }));
}
function renderAuthState() {
  $("userLabel").textContent = state.user ? state.user.email : ""; $("authButton").textContent = state.user ? "Logout" : "Login";
}
async function handleAuth() {
  if (state.user) {
    try { await fetch(`${API_BASE}/api/v1/auth/logout`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refreshToken: state.refreshToken }) }) } finally { clearSession(); await loadCart() }
  } else openAuth();
}
let registerMode = false;
function openAuth() { $("authModal").classList.remove("hidden"); $("authError").textContent = ""; $("email").focus() }
function closeAuth() { $("authModal").classList.add("hidden") }
function setAuthMode(register) {
  registerMode = register; $("authTitle").textContent = register ? "Create your account" : "Welcome back"; $("authSubtitle").textContent = register ? "Use a password with at least 12 characters." : "Login to manage your cart."; $("authSubmit").textContent = register ? "Register" : "Login"; $("toggleAuth").textContent = register ? "Already have an account? Login" : "Need an account? Register"; $("password").autocomplete = register ? "new-password" : "current-password"; $("authError").textContent = "";
}
async function submitAuth(event) {
  event.preventDefault(); $("authError").textContent = ""; const email = $("email").value.trim(), password = $("password").value;
  try {
    const endpoint = registerMode ? "/api/v1/auth/register" : "/api/v1/auth/login";
    const response = await fetch(`${API_BASE}${endpoint}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password }) });
    if (!response.ok) { let message = `Authentication failed (${response.status})`; try { const body = await response.json(); message = body.message || body.error || message } catch { } throw new Error(message) }
    saveSession(await response.json()); closeAuth(); await loadCart();
  } catch (error) { $("authError").textContent = error.message }
}
function openCart() { $("cartDrawer").classList.add("open"); $("cartDrawer").setAttribute("aria-hidden", "false"); $("drawerBackdrop").classList.remove("hidden"); loadCart() }
function closeCart() { $("cartDrawer").classList.remove("open"); $("cartDrawer").setAttribute("aria-hidden", "true"); $("drawerBackdrop").classList.add("hidden") }
function escapeHtml(value) { return String(value).replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" }[char])) }

$("searchForm").addEventListener("submit", event => { event.preventDefault(); state.query = $("searchInput").value.trim(); state.page = 0; loadProducts(false) });
$("loadMore").addEventListener("click", () => { state.page += 1; loadProducts(true) });
$("cartButton").addEventListener("click", openCart); $("closeCart").addEventListener("click", closeCart); $("drawerBackdrop").addEventListener("click", closeCart);
$("authButton").addEventListener("click", handleAuth); $("closeAuth").addEventListener("click", closeAuth); $("toggleAuth").addEventListener("click", () => setAuthMode(!registerMode)); $("authForm").addEventListener("submit", submitAuth);

renderAuthState(); setAuthMode(false); checkApi(); loadCategories().catch(error => showMessage(error.message)); loadProducts(); loadCart();