.PHONY: install migrate run test lint check
PYTHON ?= .venv/bin/python
install:
	python -m venv .venv
	.venv/bin/pip install -r requirements-dev.txt
migrate:
	DEBUG=true $(PYTHON) manage.py migrate
run:
	DEBUG=true $(PYTHON) manage.py runserver 0.0.0.0:8000
test:
	DEBUG=true $(PYTHON) manage.py test --settings=config.test_settings
lint:
	.venv/bin/ruff check accounts config store tests manage.py
	.venv/bin/ruff format --check accounts config store tests manage.py
check:
	DEBUG=true $(PYTHON) manage.py check
	DEBUG=true $(PYTHON) manage.py makemigrations --check --dry-run
