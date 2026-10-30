import os
import pathlib
import json
import uuid
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from playwright.sync_api import sync_playwright, expect

root = pathlib.Path(__file__).resolve().parents[1]
(root / "test-results").mkdir(exist_ok=True)
config = {"DEMO_ADMIN_PASSWORD": os.environ["E2E_PASSWORD"]}
base = os.environ.get("E2E_BASE_URL", "http://127.0.0.1:8000").rstrip("/")
with sync_playwright() as p:
    browser = p.chromium.launch(
        executable_path=os.environ.get("CHROMIUM_PATH", "/usr/bin/chromium"),
        headless=True,
        args=["--no-sandbox"],
    )
    page = browser.new_page(viewport={"width": 1440, "height": 1100})
    errors = []
    page.on("pageerror", lambda err: errors.append(str(err)))
    page.goto(base + "/accounts/login/")
    page.get_by_label("Username", exact=True).fill(
        os.environ.get("E2E_USERNAME", "owner")
    )
    page.get_by_label("Password", exact=True).fill(config["DEMO_ADMIN_PASSWORD"])
    page.locator(".login-card button[type=submit]").click()
    page.wait_for_url(base + "/")
    expect(
        page.get_by_role("heading", name="A clear view of your day.")
    ).to_be_visible()
    page.screenshot(path=str(root / "test-results/dashboard.png"), full_page=True)
    page.goto(base + "/products/new/")
    sku = "E2E-" + uuid.uuid4().hex[:8]
    page.locator("#id_sku").fill(sku)
    page.locator("#id_name").fill("Browser check general product")
    page.locator("#id_kind").select_option("GENERAL")
    page.locator("#id_price").fill("100.00")
    page.locator("#id_tax_rate").fill("18.00")
    page.locator("#id_reorder_level").fill("5")
    page.locator("#id_unit").fill("piece")
    page.get_by_role("button", name="Save changes").click()
    page.wait_for_url("**/products/")
    page.goto(base + "/batches/receive/")
    option = (
        page.locator("#id_product option").filter(has_text=sku).get_attribute("value")
    )
    page.locator("#id_product").select_option(option)
    page.locator("#id_number").fill("BROWSER-LOT")
    page.locator("#id_quantity").fill("10")
    page.locator("#id_unit_cost").fill("55")
    page.get_by_role("button", name="Save changes").click()
    page.wait_for_url("**/batches/")
    page.goto(base + "/pos/")
    page.locator("#pos-search").fill(sku)
    page.locator("#pos-search-form").get_by_role("button", name="Search").click()
    page.locator(".product-card").filter(has_text=sku).click()
    page.locator("#amount-paid").fill("200")
    page.get_by_role("button", name="Complete sale").click()
    page.wait_for_url("**/sales/*/")
    expect(page.locator(".grand-total")).to_contain_text("118.00")
    expect(page.locator(".receipt")).to_contain_text("82.00")
    page.screenshot(path=str(root / "test-results/receipt.png"), full_page=True)
    # Exercise the multi-line purchase UI and its full receipt transition.
    supplier_name = "Browser supplier " + sku
    page.goto(base + "/suppliers/new/")
    page.locator("#id_name").fill(supplier_name)
    page.get_by_role("button", name="Save changes").click()
    page.wait_for_url("**/suppliers/")
    page.goto(base + "/purchases/new/")
    supplier_id = (
        page.locator("#supplier option")
        .filter(has_text=supplier_name)
        .get_attribute("value")
    )
    page.locator("#supplier").select_option(supplier_id)
    page.locator(".line-product").select_option(option)
    page.locator(".line-quantity").fill("5")
    page.locator(".line-cost").fill("50.00")
    page.get_by_role("button", name="Create order", exact=True).click()
    page.wait_for_url("**/purchases/*/")
    page.locator("input[name^=number_]").fill("PURCHASE-" + sku)
    page.get_by_role("button", name="Receive full order").click()
    expect(page.locator(".page-heading .badge")).to_have_text("Received")
    # Create a fictional Rx medicine, patient, prescription and verify before dispensing.
    rx_sku = "RX-" + sku
    page.goto(base + "/products/new/")
    for field, value in {
        "sku": rx_sku,
        "name": "Browser prescription medicine",
        "price": "25.00",
        "tax_rate": "0.00",
        "reorder_level": "1",
        "unit": "capsule",
    }.items():
        page.locator("#id_" + field).fill(value)
    page.locator("#id_kind").select_option("MEDICINE")
    page.locator("#id_prescription_required").check()
    page.get_by_role("button", name="Save changes").click()
    page.wait_for_url("**/products/")
    page.goto(base + "/batches/receive/")
    rx_product_id = (
        page.locator("#id_product option")
        .filter(has_text=rx_sku)
        .get_attribute("value")
    )
    page.locator("#id_product").select_option(rx_product_id)
    page.locator("#id_number").fill("RX-LOT")
    page.locator("#id_quantity").fill("10")
    page.locator("#id_unit_cost").fill("10")
    today = datetime.now(ZoneInfo("Asia/Karachi")).date()
    page.locator("#id_expiry_date").fill((today + timedelta(days=365)).isoformat())
    page.get_by_role("button", name="Save changes").click()
    page.wait_for_url("**/batches/")
    patient_name = "Fictional patient " + sku
    page.goto(base + "/patients/new/")
    page.locator("#id_name").fill(patient_name)
    page.get_by_role("button", name="Save changes").click()
    page.wait_for_url("**/patients/")
    page.goto(base + "/prescriptions/new/")
    patient_id = (
        page.locator("#patient option")
        .filter(has_text=patient_name)
        .get_attribute("value")
    )
    page.locator("#patient").select_option(patient_id)
    page.locator("#reference").fill(rx_sku)
    page.locator("#prescriber").fill("Fictional prescriber")
    page.locator("#registration").fill("TEST-ONLY")
    page.locator("#valid-until").fill((today + timedelta(days=30)).isoformat())
    page.locator(".line-product").select_option(rx_product_id)
    page.locator(".line-quantity").fill("2")
    page.locator(".line-directions").fill("Fictional test directions only")
    page.get_by_role("button", name="Record prescription", exact=True).click()
    page.wait_for_url("**/prescriptions/")
    card = page.locator(".rx-card").filter(has_text=rx_sku)
    page.once("dialog", lambda dialog: dialog.accept())
    card.get_by_role("button", name="Verify", exact=True).click()
    expect(card.locator(".badge")).to_have_text("Verified")
    page.goto(base + "/pos/")
    page.locator("#pos-search").fill(rx_sku)
    page.locator("#pos-search-form").get_by_role("button", name="Search").click()
    page.locator(".product-card").filter(has_text=rx_sku).click()
    rx_id = (
        page.locator("#prescription option")
        .filter(has_text=rx_sku)
        .get_attribute("value")
    )
    page.locator("#prescription").select_option(rx_id)
    expect(page.locator("#patient")).to_have_value(patient_id)
    page.locator("#amount-paid").fill("100")
    page.get_by_role("button", name="Complete sale").click()
    page.wait_for_url("**/sales/*/")
    expect(page.locator(".grand-total")).to_contain_text("25.00")
    page.locator("#reason").fill("Browser test full return; fictional goods")
    page.once("dialog", lambda dialog: dialog.accept())
    page.get_by_role("button", name="Record full refund").click()
    expect(page.locator(".receipt-header .badge")).to_have_text("FULLY REFUNDED")
    page.goto(base + "/pos/")
    page.wait_for_selector(".product-card")
    page.screenshot(path=str(root / "test-results/pos.png"), full_page=True)
    page.set_viewport_size({"width": 390, "height": 844})
    page.goto(base + "/products/")
    page.screenshot(path=str(root / "test-results/mobile.png"), full_page=True)
    assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), (
        "Mobile viewport overflows horizontally"
    )
    assert not errors, errors
    print(
        json.dumps(
            {
                "browser": "system Chromium",
                "login": "passed",
                "product_create": "passed",
                "receive_stock": "passed",
                "cash_checkout": "passed",
                "purchase_and_receipt": "passed",
                "prescription_create_verify_dispense": "passed",
                "full_refund": "passed",
                "server_total": "PKR 118.00",
                "change": "PKR 82.00",
                "mobile_overflow": "none",
                "javascript_errors": errors,
            }
        )
    )
    browser.close()
