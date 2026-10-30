import uuid
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal
from threading import Barrier
from django.contrib.auth import get_user_model
from django.db import connections, close_old_connections
from django.test import TransactionTestCase, skipUnlessDBFeature
from store.models import Product, Batch, Sale
from store.services.inventory import receive_batch
from store.services.sales import checkout
from store.services.common import Conflict


@skipUnlessDBFeature("has_select_for_update")
class PostgreSQLConcurrency(TransactionTestCase):
    def setUp(self):
        self.owner = get_user_model().objects.create_user("owner", role="ADMIN")
        self.other = get_user_model().objects.create_user("other", role="CASHIER")
        self.product = Product.objects.create(
            sku="LIMITED", name="Limited stock", kind="GENERAL", price="10"
        )
        receive_batch(self.owner, self.product.pk, "LOT", 10, Decimal("5"))

    def run_pair(self, same_operator=False):
        barrier = Barrier(2)
        key = uuid.uuid4()
        product_id = self.product.pk

        def worker(user_id):
            close_old_connections()
            try:
                user = get_user_model().objects.get(pk=user_id)
                data = {
                    "idempotency_key": key,
                    "items": [{"product_id": product_id, "quantity": 8}],
                    "discount": Decimal("0"),
                    "amount_paid": Decimal("80"),
                    "payment_method": "CASH",
                }
                barrier.wait(timeout=10)
                try:
                    sale, created = checkout(user, data)
                    return "created" if created else "replayed"
                except Conflict:
                    return "insufficient"
            finally:
                connections.close_all()

        with ThreadPoolExecutor(max_workers=2) as pool:
            a = pool.submit(worker, self.owner.pk)
            b = pool.submit(worker, self.owner.pk if same_operator else self.other.pk)
            return sorted([a.result(timeout=20), b.result(timeout=20)])

    def test_parallel_checkouts_cannot_oversell(self):
        self.assertEqual(self.run_pair(), ["created", "insufficient"])
        self.assertEqual(Batch.objects.get().quantity, 2)
        self.assertEqual(Sale.objects.count(), 1)

    def test_parallel_same_key_is_only_charged_once(self):
        self.assertEqual(self.run_pair(same_operator=True), ["created", "replayed"])
        self.assertEqual(Batch.objects.get().quantity, 2)
        self.assertEqual(Sale.objects.count(), 1)
