#
# IGinX - the polystore system with high performance
# Copyright (C) Tsinghua University
# TSIGinX@gmail.com
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this program; if not, write to the Free Software Foundation,
# Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#

import joblib
import numpy as np
from UDFMLPredicate import UDFMLPredicate


class LRExtendPrice(UDFMLPredicate):
    def __init__(self):
        self.model = None

    def get_model(self):
        """Load the model from a given file path."""
        model_path = "/home/xyh/code/MLPredicate/TPC-H/linear_regression_model.pkl"
        try:
            with open(model_path, 'rb') as file:
                self.model = joblib.load(file)
            return self.model
        except Exception as e:
            raise RuntimeError(f"Failed to load model from {model_path}: {e}")

    def transform(self, data, *args, **kwargs):
        """Apply the loaded model to the input data."""
        res = self.buildHeader(data)
        if self.get_model() is None:
            raise ValueError("Model is not loaded. Please load a model using get_model().")

        try:
            input_data = np.array(data[2][1:], dtype=float)  # Ensure numerical data
            input_data = input_data.reshape(1, -1)
            predictions = self.model.predict(input_data)
            res.append([float(predictions[0])])
        except Exception as e:
            raise RuntimeError(f"Error during model prediction: {e}")

        return res

    def buildHeader(self, data):
        """Format the output with headers."""
        return [["udf_lr(" + ", ".join(data[0][1:]) +")"], ["double"]]
